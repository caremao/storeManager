package com.caremao.storemanager.srd;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SrdItemRepository extends JpaRepository<SrdItem, String> {

    List<SrdItem> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
