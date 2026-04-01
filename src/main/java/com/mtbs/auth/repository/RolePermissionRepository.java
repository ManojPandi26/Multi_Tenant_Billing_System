package com.mtbs.auth.repository;

import com.mtbs.auth.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findAllByRoleId(Long roleId);

    void deleteAllByRoleId(Long roleId);

    @org.springframework.data.jpa.repository.Query("SELECT p.name FROM RolePermission rp JOIN rp.permission p WHERE rp.role.id = :roleId")
    List<String> findPermissionNamesByRoleId(@org.springframework.data.repository.query.Param("roleId") Long roleId);

    List<RolePermission> findByRoleId(Long roleId);

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
