package com.project.CarRental2.controller;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.project.CarRental2.model.Car;
import com.project.CarRental2.model.CarReview;
import com.project.CarRental2.model.User;
import com.project.CarRental2.service.CarReviewService;
import com.project.CarRental2.service.CarService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class CarReviewController {

    @Autowired
    private CarReviewService carReviewService;

    @Autowired
    private CarService carService;

    @PostMapping("/submit-review")
    public String submitReview(@RequestParam(name = "carId", defaultValue = "0") int carId,
            @RequestParam(name = "rating", defaultValue = "0") int rating,
            @RequestParam(name = "comment", defaultValue = "") String comment,
            HttpServletRequest request,
            RedirectAttributes ra) {

        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("sesionUser");

        if (user == null) {
            ra.addFlashAttribute("messege_error", "Bạn cần đăng nhập để đánh giá");
            return "redirect:/login";
        }

        Car car = carService.getACarByIdCar(carId);
        if (car == null) {
            return "redirect:/";
        }

        if (rating < 1 || rating > 5 || comment.trim().isEmpty()) {
            ra.addFlashAttribute("messege_error", "Vui lòng chọn số sao và nhập nội dung đánh giá");
            return "redirect:/car-detail/" + carId + "/" + car.getNameCar();
        }

        CarReview review = new CarReview(car, user, rating, comment, new Date());
        carReviewService.saveReview(review);

        ra.addFlashAttribute("messege_success", "Cảm ơn bạn đã gửi đánh giá!");

        // Redirect back to car detail page
        return "redirect:/car-detail/" + carId + "/" + car.getNameCar();
    }
}
