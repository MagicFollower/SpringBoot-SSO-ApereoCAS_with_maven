package com.example.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 条线（t_channel）。
 */
@Getter
@Setter
@Table(name = "t_channel")
public class Channel {

    @Id
    @Column(name = "channel_code")
    private String channelCode;

    @Column(name = "channel_name")
    private String channelName;

    private Integer status;

    private String theme;

    @Column(name = "mfa_required")
    private Integer mfaRequired;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
