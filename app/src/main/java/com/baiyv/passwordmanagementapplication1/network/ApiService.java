package com.baiyv.passwordmanagementapplication1.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 后端API接口定义
 */
public interface ApiService {

    @POST("backup/email")
    Call<ApiResponse<Void>> sendBackupEmail(@Body BackupEmailRequest request);

    class ApiResponse<T> {
        private int code;
        private String message;
        private T data;

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }

        public boolean isSuccess() { return code == 200; }
    }

    class BackupEmailRequest {
        private String email;
        private String backupText;

        public BackupEmailRequest(String email, String backupText) {
            this.email = email;
            this.backupText = backupText;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getBackupText() { return backupText; }
        public void setBackupText(String backupText) { this.backupText = backupText; }
    }
}