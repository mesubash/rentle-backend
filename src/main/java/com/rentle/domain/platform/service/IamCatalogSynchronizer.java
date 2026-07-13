package com.rentle.domain.platform.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.platform.catalog.PermissionCatalog;
import com.rentle.domain.platform.catalog.PermissionDefinition;
import com.rentle.domain.platform.catalog.RoleSeeds;
import com.rentle.domain.platform.model.Permission;
import com.rentle.domain.platform.model.Role;
import com.rentle.domain.platform.model.RolePermission;
import com.rentle.domain.platform.repository.PermissionRepository;
import com.rentle.domain.platform.repository.RolePermissionRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IamCatalogSynchronizer {

    private final List<PermissionCatalog> catalogs;
    private final RoleSeeds roleSeeds;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RentleProperties properties;

    public IamCatalogSynchronizer(List<PermissionCatalog> catalogs,
                                  RoleSeeds roleSeeds,
                                  PermissionRepository permissionRepository,
                                  RoleRepository roleRepository,
                                  RolePermissionRepository rolePermissionRepository,
                                  RentleProperties properties) {
        this.catalogs = catalogs;
        this.roleSeeds = roleSeeds;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeAtStartup() {
        if (properties.iam().syncCatalog()) {
            synchronize();
        }
    }

    @Transactional
    public void synchronize() {
        Map<String, Permission> permissionsByKey = synchronizePermissions();

        roleSeeds.roles().forEach((name, definition) -> {
            Role role = roleRepository.findByName(name).orElse(null);
            boolean created = role == null;
            if (created) {
                role = new Role();
                role.setName(name);
                role.setDisplayName(definition.displayName());
                role.setDescription(definition.description());
                role.setIsSystemRole(definition.systemRole());
                role = roleRepository.save(role);
            }

            if (created || Boolean.TRUE.equals(role.getIsSystemRole())) {
                reconcileRolePermissions(role, definition, permissionsByKey);
            }
        });
    }

    private Map<String, Permission> synchronizePermissions() {
        Map<String, Permission> permissionsByKey = new LinkedHashMap<>();
        for (PermissionCatalog catalog : catalogs) {
            for (PermissionDefinition definition : catalog.permissions()) {
                Permission permission = permissionRepository.findByKey(definition.key())
                        .orElseGet(() -> permissionRepository.save(toEntity(definition)));
                permissionsByKey.put(definition.key(), permission);
            }
        }
        return permissionsByKey;
    }

    private Permission toEntity(PermissionDefinition definition) {
        Permission permission = new Permission();
        permission.setKey(definition.key());
        permission.setDomain(definition.domain());
        permission.setResource(definition.resource());
        permission.setAction(definition.action());
        permission.setDescription(definition.description());
        return permission;
    }

    private void reconcileRolePermissions(Role role,
                                          RoleSeeds.RoleSeed definition,
                                          Map<String, Permission> permissionsByKey) {
        rolePermissionRepository.deleteByIdRoleId(role.getId());
        List<RolePermission> rolePermissions = definition.permissionKeys().stream()
                .map(permissionsByKey::get)
                .map(permission -> new RolePermission(role, permission))
                .toList();
        rolePermissionRepository.saveAll(rolePermissions);
    }
}
