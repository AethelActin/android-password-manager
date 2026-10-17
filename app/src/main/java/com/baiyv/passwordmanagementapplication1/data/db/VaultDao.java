package com.baiyv.passwordmanagementapplication1.data.db;
import androidx.room.*;
import com.baiyv.passwordmanagementapplication1.data.model.VaultMetadata;
@Dao public interface VaultDao {
 @Query("SELECT * FROM vault_metadata WHERE id=1") VaultMetadata get();
 @Insert(onConflict=OnConflictStrategy.REPLACE) void put(VaultMetadata metadata);
 @Query("DELETE FROM vault_metadata") void clear();
}
