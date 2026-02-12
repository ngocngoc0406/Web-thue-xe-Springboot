package com.project.CarRental2.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CarReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idReview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_car")
    @JsonIgnore
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user")
    @JsonIgnore
    private User user;

    private int rating; // 1 to 5 stars

    @Column(columnDefinition = "nvarchar(2000)")
    private String comment;

    private Date createDate;

    public CarReview(Car car, User user, int rating, String comment, Date createDate) {
        this.car = car;
        this.user = user;
        this.rating = rating;
        this.comment = comment;
        this.createDate = createDate;
    }
}
