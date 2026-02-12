package com.project.CarRental2.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.CarRental2.model.CarViewHistory;

/**
 * Repository cho CarViewHistory - hỗ trợ Collaborative Filtering
 */
@Repository
public interface CarViewHistoryRepository extends JpaRepository<CarViewHistory, Integer> {

    /**
     * Lấy lịch sử xem xe của một user, sắp xếp theo số lần xem giảm dần
     */
    List<CarViewHistory> findByIdUserOrderByViewCountDesc(int idUser);

    /**
     * Lấy top N xe được xem nhiều nhất của user
     */
    @Query("SELECT cvh FROM CarViewHistory cvh WHERE cvh.idUser = :idUser ORDER BY cvh.viewCount DESC")
    List<CarViewHistory> findTopViewedByUser(@Param("idUser") int idUser);

    /**
     * Tìm bản ghi xem xe cụ thể của user
     */
    Optional<CarViewHistory> findByIdUserAndIdCar(int idUser, int idCar);

    /**
     * Tìm các user đã xem một xe cụ thể (loại trừ user hiện tại)
     */
    @Query("SELECT DISTINCT cvh.idUser FROM CarViewHistory cvh WHERE cvh.idCar = :idCar AND cvh.idUser != :excludeUser")
    List<Integer> findUsersByCarId(@Param("idCar") int idCar, @Param("excludeUser") int excludeUser);

    /**
     * Lấy xe được xem nhiều nhất bởi các user khác (Collaborative Filtering)
     * Loại trừ các xe mà user hiện tại đã xem
     */
    @Query("SELECT cvh.idCar, SUM(cvh.viewCount) as totalViews FROM CarViewHistory cvh " +
            "WHERE cvh.idUser IN :similarUserIds AND cvh.idCar NOT IN :excludeCarIds " +
            "GROUP BY cvh.idCar ORDER BY totalViews DESC")
    List<Object[]> findRecommendedCars(
            @Param("similarUserIds") List<Integer> similarUserIds,
            @Param("excludeCarIds") List<Integer> excludeCarIds);

    /**
     * Lấy các xe user đã xem
     */
    @Query("SELECT cvh.idCar FROM CarViewHistory cvh WHERE cvh.idUser = :idUser")
    List<Integer> findViewedCarIdsByUser(@Param("idUser") int idUser);

    /**
     * Lấy top xe phổ biến nhất (dùng cho user mới chưa có lịch sử)
     */
    @Query("SELECT cvh.idCar, SUM(cvh.viewCount) as totalViews FROM CarViewHistory cvh " +
            "GROUP BY cvh.idCar ORDER BY totalViews DESC")
    List<Object[]> findMostPopularCars();

    /**
     * Đếm số user đã xem một xe
     */
    @Query("SELECT COUNT(DISTINCT cvh.idUser) FROM CarViewHistory cvh WHERE cvh.idCar = :idCar")
    int countUsersViewedCar(@Param("idCar") int idCar);
}
