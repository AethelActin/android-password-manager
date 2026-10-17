package com.baiyv.passwordmanagementapplication1.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "passwords")
public class PasswordEntry {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;           // 标题（如：建设银行APP）
    private String username;        // 用户名/账号
    private String encryptedPassword; // 加密后的密码
    private String website;         // 网站/应用
    private String category;        // 分类
    private String notes;           // 备注
    private int strengthLevel;      // 密码强度等级 1-4
    private long createTime;        // 创建时间
    private long updateTime;        // 更新时间
    private boolean isFavorite;     // 是否收藏

    // 构造函数
    public PasswordEntry() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getStrengthLevel() { return strengthLevel; }
    public void setStrengthLevel(int strengthLevel) { this.strengthLevel = strengthLevel; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public long getUpdateTime() { return updateTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
}