package com.baiyv.passwordmanagementapplication1.data.model;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName="vault_metadata")
public class VaultMetadata {
 @PrimaryKey public int id = 1;
 public String envelope;
 public String totpSecret;
}
