package com.project.CarRental2.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity lưu lịch sử người dùng xem xe
 * Dùng để xây dựng hệ thống gợi ý Collaborative Filtering
 */
@Entity
@Table(name = "car_view_history", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "id_user", "id_car" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarViewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "id_user", nullable = false)
    private int idUser;

    @Column(name = "id_car", nullable = false)
    private int idCar;

    @Column(name = "view_count", columnDefinition = "int default 1")
    private int viewCount = 1;

    @Column(name = "last_viewed_at")
    private Date lastViewedAt;

    @Column(name = "created_at")
    private Date createdAt;

    public CarViewHistory(int idUser, int idCar) {
        this.idUser = idUser;
        this.idCar = idCar;
        this.viewCount = 1;
        this.createdAt = new Date();
        this.lastViewedAt = new Date();
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.lastViewedAt = new Date();
    }
}
