package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.UserResponseDTO;
import com.foodsquad.FoodSquad.model.dto.UserUpdateDTO;
import com.foodsquad.FoodSquad.model.entity.User;
import com.foodsquad.FoodSquad.model.entity.UserRole;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private OrderRepository orderRepository;

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

    private void checkOwnership(String userId) {
        User currentUser = getCurrentUser();
        if (!currentUser.getId().equals(userId) && !currentUser.getRole().equals(UserRole.ADMIN) && !currentUser.getRole().equals(UserRole.MODERATOR)) {
            throw new IllegalArgumentException("Access denied");
        }
    }


    public List<UserResponseDTO> getAllUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdOn"));
        Page<User> userPage = userRepository.findAll(pageable);
        return userPage.stream()
                .map(user -> {
                    long ordersCount = orderRepository.countByUserId(user.getId());
                    UserResponseDTO userResponseDTO = modelMapper.map(user, UserResponseDTO.class);
                    userResponseDTO.setOrdersCount(ordersCount);
                    return userResponseDTO;
                })
                .collect(Collectors.toList());
    }

    public ResponseEntity<UserResponseDTO> getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found for ID: " + id));
        long ordersCount = orderRepository.countByUserId(id);
        UserResponseDTO userDTO = modelMapper.map(user, UserResponseDTO.class);
        userDTO.setOrdersCount(ordersCount);
        return ResponseEntity.ok(userDTO);
    }

    @Transactional
    public ResponseEntity<UserResponseDTO> updateUser(String id, UserUpdateDTO userUpdateDTO) {
        User currentUser = getCurrentUser();
        checkOwnership(id);
        // Check if the current user is trying to update their own role
        if (currentUser.getId().equals(id) && !currentUser.getRole().name().equals(userUpdateDTO.getRole())) {
            throw new IllegalArgumentException("Users cannot update their own role.");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found for ID: " + id));

        // Check if the current user is a normal user and is trying to change the role to something other than NORMAL
        if (currentUser.getRole().equals(UserRole.NORMAL) && !userUpdateDTO.getRole().equals(UserRole.NORMAL.name())) {
            throw new IllegalArgumentException("Normal users cannot change roles.");
        }

        // Check if the current user is not an admin but is trying to set the role to admin
        if (!currentUser.getRole().equals(UserRole.ADMIN) && userUpdateDTO.getRole().equals(UserRole.ADMIN.name())) {
            throw new IllegalArgumentException("Only admin users can assign the admin role.");
        }

        // Check if the user role is ADMIN and is being changed
        if (user.getRole() == UserRole.ADMIN && !userUpdateDTO.getRole().equals(UserRole.ADMIN.name())) {
            throw new IllegalArgumentException("Admin user role cannot be changed");
        }

        user.setName(userUpdateDTO.getName());
        user.setRole(UserRole.valueOf(userUpdateDTO.getRole()));
        user.setImageUrl(userUpdateDTO.getImageUrl());
        user.setPhoneNumber(userUpdateDTO.getPhoneNumber());

        userRepository.save(user);
        long ordersCount = orderRepository.countByUserId(id);
        UserResponseDTO updatedUserDTO = modelMapper.map(user, UserResponseDTO.class);
        updatedUserDTO.setOrdersCount(ordersCount);
        return ResponseEntity.ok(updatedUserDTO);
    }

    @Transactional
    public ResponseEntity<Map<String, String>> deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found for ID: " + id));
        if (user.getRole().equals(UserRole.ADMIN)) {
            throw new IllegalArgumentException("Admin users cannot be deleted.");
        }

        // Delete menu item references in order_menu_item (w/o this foreign key table error appears)
        user.getMenuItems().forEach(menuItem -> {
            orderRepository.removeMenuItemReferences(menuItem.getId());
        });

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "User successfully deleted."));
    }
}
