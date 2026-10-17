package com.baiyv.passwordmanagementapplication1.data.db;


import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.baiyv.passwordmanagementapplication1.data.model.Category;

import java.util.List;

@Dao
public interface CategoryDao {

    @Insert
    long insert(Category category);

    @Delete
    void delete(Category category);

    @Query("SELECT * FROM categories ORDER BY name ASC")
    List<Category> getAllCategories();

    @Query("SELECT * FROM categories WHERE name = :name")
    Category getCategoryByName(String name);
}