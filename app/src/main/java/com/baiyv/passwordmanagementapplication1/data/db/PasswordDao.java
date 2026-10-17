package com.baiyv.passwordmanagementapplication1.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.baiyv.passwordmanagementapplication1.data.model.PasswordEntry;

import java.util.List;

@Dao
public interface PasswordDao {

    @Insert
    long insert(PasswordEntry password);

    @Insert
    void insertAll(List<PasswordEntry> passwords);

    @Update
    int update(PasswordEntry password);

    @Delete
    int delete(PasswordEntry password);

    @Query("SELECT * FROM passwords ORDER BY updateTime DESC")
    List<PasswordEntry> getAllPasswords();

    @Query("SELECT * FROM passwords WHERE id = :id")
    PasswordEntry getPasswordById(int id);

    @Query("SELECT * FROM passwords WHERE title LIKE '%' || :keyword || '%' OR username LIKE '%' || :keyword || '%' ORDER BY updateTime DESC")
    List<PasswordEntry> searchPasswords(String keyword);

    @Query("SELECT COUNT(*) FROM passwords")
    int getPasswordCount();

    @Query("DELETE FROM passwords")
    void deleteAll();
}