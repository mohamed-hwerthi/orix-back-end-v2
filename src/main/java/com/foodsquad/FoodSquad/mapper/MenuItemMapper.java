package com.foodsquad.FoodSquad.mapper;


import com.foodsquad.FoodSquad.model.dto.MenuItemDTO;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;


import java.util.List;

@Mapper( componentModel = MappingConstants.ComponentModel.SPRING , uses = {CategoryMapper.class, MediaMapper.class ,TaxMapper.class})

public interface MenuItemMapper {
   MenuItem toEntity(MenuItemDTO menuItemDTO);
   MenuItemDTO toDto(MenuItem menuItem);
   List<MenuItemDTO> toDtoList(List<MenuItem> menuItems);
   @Mapping(target = "id", ignore = true)
   @Mapping(target = "user", ignore = true)
   @Mapping(target = "currency", ignore = true)
   @Mapping(target = "tax", ignore = true)
   @Mapping(target = "createdOn", ignore = true)
   @Mapping(target = "reviews", ignore = true)
   @Mapping(target = "menus", ignore = true)
    void updateMenuItemFromDto(MenuItemDTO dto, @MappingTarget MenuItem entity);

}
