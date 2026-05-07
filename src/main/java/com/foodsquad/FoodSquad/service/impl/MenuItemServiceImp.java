package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.mapper.*;
import com.foodsquad.FoodSquad.model.dto.*;
import com.foodsquad.FoodSquad.model.entity.*;
import com.foodsquad.FoodSquad.model.dto.*;
import com.foodsquad.FoodSquad.model.entity.Category;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.MenuItemCategory;
import com.foodsquad.FoodSquad.model.entity.User;
import com.foodsquad.FoodSquad.model.entity.UserRole;
import com.foodsquad.FoodSquad.repository.*;
import com.foodsquad.FoodSquad.service.declaration.CategoryService;
import com.foodsquad.FoodSquad.service.declaration.MenuItemService;
import com.foodsquad.FoodSquad.service.declaration.TaxService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MenuItemServiceImp implements MenuItemService {

    private final Logger logger = LoggerFactory.getLogger(MenuItemServiceImp.class);

    private final MenuItemRepository menuItemRepository;

    private final OrderRepository orderRepository;

    private final ReviewRepository reviewRepository;

    private final UserRepository userRepository;

    private final ModelMapper modelMapper;


    private final CategoryMapper categoryMapper;

    private final MenuItemMapper menuItemMapper;

    private final MediaMapper mediaMapper;

    private final TaxService taxService;

    private final CurrencyMapper currencyMapper;

    private final TaxMapper taxMapper;
    private final TaxRepository taxRepository;

    private final CurrencyRepository currencyRepository;

    public MenuItemServiceImp(MenuItemRepository menuItemRepository, OrderRepository orderRepository, ReviewRepository reviewRepository, UserRepository userRepository, ModelMapper modelMapper, CategoryService categoryService, CategoryMapper categoryMapper, MenuItemMapper menuItemMapper, MediaMapper mediaMapper, TaxService taxService, CurrencyMapper currencyMapper, TaxMapper taxMapper, TaxRepository taxRepository, CurrencyRepository currencyRepository) {

        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.categoryMapper = categoryMapper;
        this.menuItemMapper = menuItemMapper;
        this.mediaMapper = mediaMapper;
        this.taxService = taxService;
        this.currencyMapper = currencyMapper;
        this.taxMapper = taxMapper;
        this.taxRepository = taxRepository;
        this.currencyRepository = currencyRepository;
    }

    private User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Object principal = auth.getPrincipal();
        String email;
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else if (principal instanceof String s) {
            email = s;
        } else {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Session expired");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "User not found for email: " + email));
    }

    private void checkOwnership(MenuItem menuItem) {

        User currentUser = getCurrentUser();
        if (!menuItem.getUser().equals(currentUser) && !currentUser.getRole().equals(UserRole.ADMIN) && !currentUser.getRole().equals(UserRole.MODERATOR)) {
            throw new IllegalArgumentException("Access denied");
        }
    }

    public ResponseEntity<MenuItemDTO> createMenuItem(MenuItemDTO menuItemDTO) {

        logger.debug("Creating menu item: {}", menuItemDTO);
        MenuItem menuItem = menuItemMapper.toEntity(menuItemDTO);
        User currentUser = getCurrentUser();
        menuItem.setUser(currentUser);

        // Defensive defaults for non-null entity fields that the DTO may not send
        if (menuItem.getIsActive() == null) menuItem.setIsActive(true);
        if (menuItem.getAllowNegativeStock() == null) menuItem.setAllowNegativeStock(false);
        if (menuItem.getHasExpiryDate() == null) menuItem.setHasExpiryDate(false);
        if (menuItem.getStockQuantity() == null) menuItem.setStockQuantity(0);
        if (menuItem.getMinStockAlert() == null) menuItem.setMinStockAlert(0);
        if (menuItem.getPrice() == null) menuItem.setPrice(0.0);

        // Convert empty strings to null on UNIQUE columns
        // (DB UNIQUE accepts multiple NULLs but rejects multiple "")
        if (menuItem.getSku() != null && menuItem.getSku().isBlank()) menuItem.setSku(null);
        if (menuItem.getBarCode() != null && menuItem.getBarCode().isBlank()) menuItem.setBarCode(null);

        // Auto-assign currency: explicit ID > first available
        if (menuItemDTO.getCurrency() != null && menuItemDTO.getCurrency().getId() != null) {
            currencyRepository.findById(menuItemDTO.getCurrency().getId()).ifPresent(menuItem::setCurrency);
        } else {
            currencyRepository.findAll().stream().findFirst().ifPresent(menuItem::setCurrency);
        }

        if (menuItemDTO.getTax() != null) {
            Tax tax = this.taxService.createTax(menuItemDTO);
            menuItem.setTax(tax);

            double price = menuItem.getPrice();
            double taxRate = (menuItemDTO.getTax().getRate())/100; // Assuming rate is 0.20 for 20%
            double priceWithTax = price * (1 + taxRate);

            // Get currency scale (default to 2 if not specified)
            int scale = menuItem.getCurrency() != null ? menuItem.getCurrency().getScale() : 2;

            // Round to the correct number of decimal places
            double roundedPrice = roundToScale(priceWithTax, scale);
            menuItem.setPrice(roundedPrice);
        }
        MenuItem savedMenuItem = menuItemRepository.save(menuItem);
        MenuItemDTO responseDTO = menuItemMapper.toDto(savedMenuItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @Override
    public PaginatedResponseDTO<MenuItemDTO> searchMenuItemsByQuery(MenuItemFilterByCategoryAndQueryRequestDTO menuItemFilterByCategoryAndQueryRequestDTO, Pageable pageable) {

        logger.debug("Searching menu items by query and categoriesIds ");
        Page<MenuItem> menuItemPage;
        if (ObjectUtils.isEmpty(menuItemFilterByCategoryAndQueryRequestDTO.getCategoriesIds())) {
            menuItemPage = menuItemRepository.findByQuery(menuItemFilterByCategoryAndQueryRequestDTO.getQuery(), pageable);

        } else {
            menuItemPage = menuItemRepository.filterByQueryAndCategories(menuItemFilterByCategoryAndQueryRequestDTO.getQuery(), menuItemFilterByCategoryAndQueryRequestDTO.getCategoriesIds(), pageable);

        }

        List<MenuItem> menuItems = menuItemPage.getContent();
        List<MenuItemDTO> menuItemDTOs = menuItems.stream()
                .map(menuItemMapper::toDto)
                .toList();
        return new PaginatedResponseDTO<>(menuItemDTOs, menuItemPage.getTotalElements());


    }

    @Override
    public PaginatedResponseDTO<MenuItemDTO> searchMenuItemsByQuery(String query, Pageable pageable) {

        logger.debug("Searching menu items by query: {}", query);


        Page<MenuItem> menuItemPage = menuItemRepository.findByQuery(query, pageable);
        List<MenuItem> menuItems = menuItemPage.getContent();
        List<MenuItemDTO> menuItemDTOs = menuItems.stream()
                .map(menuItemMapper::toDto)
                .toList();
        return new PaginatedResponseDTO<>(menuItemDTOs, menuItemPage.getTotalElements());


    }

    public ResponseEntity<MenuItemDTO> getMenuItemById(Long id) {

        logger.debug("Getting menu item by ID: {}", id);

        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MenuItem not found for ID: " + id));
        Integer salesCount = orderRepository.sumQuantityByMenuItemId(menuItem.getId());
        if (salesCount == null) {
            salesCount = 0;
        }
        long reviewCount = reviewRepository.countByMenuItemId(menuItem.getId());
        Double averageRating = reviewRepository.findAverageRatingByMenuItemId(menuItem.getId());
        if (averageRating == null) {
            averageRating = 0.0;
        }
        averageRating = Math.round(averageRating * 10.0) / 10.0;
        List<CategoryDTO> menuItemCategories = menuItem.getCategories().stream().map(categoryMapper::toDto).toList();
        List<MediaDTO> menuItemMedia = menuItem.getMedias().stream().map(mediaMapper::toDto).toList();
        CurrencyDTO menuItemCurrency = currencyMapper.toDto(menuItem.getCurrency());
        TaxDTO menuItemTax = taxMapper.toDto(menuItem.getTax());
        MenuItemDTO menuItemDTO = new MenuItemDTO(menuItem, salesCount, reviewCount, averageRating, menuItemCategories, menuItemMedia, menuItemCurrency ,menuItemTax);
        return ResponseEntity.ok(menuItemDTO);
    }

    public PaginatedResponseDTO<MenuItemDTO> getAllMenuItems(int page, int limit, String sortBy, boolean desc, Long categoryId, String isDefault, String priceSortDirection) {

        logger.debug("Getting all menu items  with some filters");

        Pageable pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdOn"));
        Page<MenuItem> menuItemPage;

        if (categoryId != null) {
            menuItemPage = menuItemRepository.findByCategoryId(categoryId, pageable);
        } else {
            menuItemPage = menuItemRepository.findAll(pageable);
        }
        List<MenuItemDTO> menuItems = menuItemPage.stream()
                .map(menuItem -> {
                    Integer salesCount = orderRepository.sumQuantityByMenuItemId(menuItem.getId());
                    if (salesCount == null) {
                        salesCount = 0;
                    }
                    long reviewCount = reviewRepository.countByMenuItemId(menuItem.getId());
                    Double averageRating = reviewRepository.findAverageRatingByMenuItemId(menuItem.getId());
                    if (averageRating == null) {
                        averageRating = 0.0; // Default to 0.0 if there are no reviews
                    }
                    averageRating = Math.round(averageRating * 10.0) / 10.0; // Format to 1 decimal place
                    List<CategoryDTO> menuItemCategories = menuItem.getCategories().stream().map(categoryMapper::toDto).toList();
                    CurrencyDTO menuItemCurrency = currencyMapper.toDto(menuItem.getCurrency());
                    List<MediaDTO> menuItemMedia = menuItem.getMedias().stream().map(mediaMapper::toDto).toList();
                    TaxDTO menuItemTax = taxMapper.toDto(menuItem.getTax());

                    return new MenuItemDTO(menuItem, salesCount, reviewCount, averageRating, menuItemCategories, menuItemMedia, menuItemCurrency ,menuItemTax);
                })
                .collect(Collectors.toList());


        // Sort by price
        if (priceSortDirection != null && !priceSortDirection.isEmpty()) {
            menuItems.sort((a, b) -> {
                if (priceSortDirection.equals("asc")) {
                    return Double.compare(a.getPrice(), b.getPrice());
                } else {
                    return Double.compare(b.getPrice(), a.getPrice());
                }
            });
        }

        // Sort by salesCount if specified (workaround for now)
        if ("salesCount".equals(sortBy)) {
            menuItems.sort((item1, item2) -> desc
                    ? item2.getSalesCount().compareTo(item1.getSalesCount())
                    : item1.getSalesCount().compareTo(item2.getSalesCount()));
        }

        // PaginatedResponseDTO with the list of items and the total count
        return new PaginatedResponseDTO<>(menuItems, menuItemPage.getTotalElements());
    }

    @Transactional
    public ResponseEntity<MenuItemDTO> updateMenuItem(Long id, MenuItemDTO menuItemDTO) {
        logger.debug("Updating menu item with ID: {}", id);

        MenuItem existingMenuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MenuItem not found for ID: " + id));

        checkOwnership(existingMenuItem);

        // Handle currency update
        if (menuItemDTO.getCurrency() != null && menuItemDTO.getCurrency().getId() != null) {
            Currency currency = currencyRepository.findById(menuItemDTO.getCurrency().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Currency not found"));
            existingMenuItem.setCurrency(currency);
        }

        // Handle tax update/creation and price calculation
        if (menuItemDTO.getTax() != null) {
            Tax existingTax = existingMenuItem.getTax();
            double newPrice = menuItemDTO.getPrice();
            double newTaxRate = menuItemDTO.getTax().getRate();

            // Get existing values (use 0 if no existing tax)
            double existingTaxRate = (existingTax != null) ? existingTax.getRate() : 0;
            double existingPriceTTC = existingMenuItem.getPrice();

            // Update or create tax
            Tax tax = existingTax != null ? existingTax : new Tax();
            tax.setRate(newTaxRate);
            tax.setName(menuItemDTO.getTax().getName());
            tax = taxRepository.save(tax);
            existingMenuItem.setTax(tax);

            // Get currency scale
            int scale = existingMenuItem.getCurrency() != null ?
                    existingMenuItem.getCurrency().getScale() : 2;

            // Case 1: Both price and tax rate unchanged - keep existing price
            if (Math.abs(newPrice - existingPriceTTC) < 0.0001 &&
                    Math.abs(newTaxRate - existingTaxRate) < 0.0001) {
                // No recalculation needed
                menuItemDTO.setPrice(existingPriceTTC);
            }
            // Case 2: Price changed, tax rate unchanged
            else if (Math.abs(newPrice - existingPriceTTC) >= 0.0001 &&
                    Math.abs(newTaxRate - existingTaxRate) < 0.0001) {
                // Calculate HT from existing TTC price and apply to new price
                double existingPriceHT = existingPriceTTC / (1 + existingTaxRate / 100);
                double priceWithTax = newPrice * (1 + newTaxRate / 100);
                existingMenuItem.setPrice(roundToScale(priceWithTax, scale));
                menuItemDTO.setPrice(existingMenuItem.getPrice());

            }
            // Case 3: Price unchanged, tax rate changed
            else if (Math.abs(newPrice - existingPriceTTC) < 0.0001 &&
                    Math.abs(newTaxRate - existingTaxRate) >= 0.0001) {
                // Calculate HT from existing TTC price and apply new tax rate
                double priceHT = existingPriceTTC / (1 + existingTaxRate / 100);
                double priceWithNewTax = priceHT * (1 + newTaxRate / 100);
                existingMenuItem.setPrice(roundToScale(priceWithNewTax, scale));
                menuItemDTO.setPrice(existingMenuItem.getPrice());
            }
            // Case 4: Both price and tax rate changed
            else {
                // Calculate new TTC price with new tax rate
                double priceWithTax = newPrice * (1 + newTaxRate / 100);
                existingMenuItem.setPrice(roundToScale(priceWithTax, scale));
                menuItemDTO.setPrice(existingMenuItem.getPrice());
            }
        }

        menuItemMapper.updateMenuItemFromDto(menuItemDTO, existingMenuItem);
        MenuItem savedMenuItem = menuItemRepository.save(existingMenuItem);
        MenuItemDTO responseDTO = modelMapper.map(savedMenuItem, MenuItemDTO.class);

        return ResponseEntity.ok(responseDTO);
    }

    @Transactional
    public ResponseEntity<Map<String, String>> deleteMenuItem(Long id) {

        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MenuItem not found for ID: " + id));
        checkOwnership(menuItem);

        orderRepository.removeMenuItemReferences(menuItem.getId());

        menuItemRepository.delete(menuItem);
        return ResponseEntity.ok(Map.of("message", "Menu Item successfully deleted"));
    }

    @Transactional
    public ResponseEntity<Map<String, String>> deleteMenuItemsByIds(List<Long> ids) {

        List<MenuItem> menuItems = menuItemRepository.findAllById(ids);
        if (menuItems.isEmpty()) {
            throw new EntityNotFoundException("No MenuItems found for the given IDs");
        }
        menuItems.forEach(menuItem -> {
            checkOwnership(menuItem);
            orderRepository.removeMenuItemReferences(menuItem.getId()); // Remove references in order_menu_item table
            menuItemRepository.delete(menuItem);
        });
        return ResponseEntity.ok(Map.of("message", "Menu Items successfully deleted"));
    }


    //  method to fetch multiple menu items by their IDs
    public ResponseEntity<List<MenuItemDTO>> getMenuItemsByIds(List<Long> ids) {

        logger.debug("Getting menu items by IDs: {}", ids);

        List<MenuItem> menuItems = menuItemRepository.findAllById(ids);
        if (menuItems.isEmpty()) {
            throw new EntityNotFoundException("No MenuItems found for the given IDs");
        }
        List<MenuItemDTO> menuItemDTOs = menuItems.stream()
                .map(menuItem -> {
                    Integer salesCount = orderRepository.sumQuantityByMenuItemId(menuItem.getId());
                    if (salesCount == null) {
                        salesCount = 0;
                    }
                    long reviewCount = reviewRepository.countByMenuItemId(menuItem.getId());
                    Double averageRating = reviewRepository.findAverageRatingByMenuItemId(menuItem.getId());
                    if (averageRating == null) {
                        averageRating = 0.0; // Default to 0.0 if there are no reviews
                    }
                    averageRating = Math.round(averageRating * 10.0) / 10.0; // Format to 1 decimal place
                    List<CategoryDTO> menuItemCategories = menuItem.getCategories().stream().map(categoryMapper::toDto).toList();
                    CurrencyDTO menuItemCurrency = currencyMapper.toDto(menuItem.getCurrency());
                    List<MediaDTO> menuItemMedia = menuItem.getMedias().stream().map(mediaMapper::toDto).toList();


                    return new MenuItemDTO(menuItem, salesCount, reviewCount, averageRating, menuItemCategories, menuItemMedia, menuItemCurrency);
                })
                .toList();
        return ResponseEntity.ok(menuItemDTOs);
    }

    @Override
    public MenuItemDTO findByBarCode(String qrCode) {

        return menuItemRepository.findByBarCode(qrCode)
                .map(menuItemMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("MenuItem not found for qrCode"));
    }

    @Override
    public List<MenuItemDTO> getLowStockItems() {

        logger.debug("Getting low stock menu items");
        return menuItemRepository.findLowStockItems().stream()
                .map(menuItem -> {
                    List<CategoryDTO> cats = menuItem.getCategories().stream().map(categoryMapper::toDto).toList();
                    List<MediaDTO> medias = menuItem.getMedias().stream().map(mediaMapper::toDto).toList();
                    CurrencyDTO currency = currencyMapper.toDto(menuItem.getCurrency());
                    TaxDTO tax = taxMapper.toDto(menuItem.getTax());
                    return new MenuItemDTO(menuItem, 0, 0L, 0.0, cats, medias, currency, tax);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> getStockSummary() {
        return menuItemRepository.findStockSummary();
    }
    private double roundToScale(double value, int scale) {
        if (scale < 0) throw new IllegalArgumentException("Scale must be a positive integer");

        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

}
