package com.project.CarRental2.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.project.CarRental2.model.BrandCar;
import com.project.CarRental2.repository.BrandCarRepository;

@Component
public class BrandCarConverter implements Converter<String, BrandCar> {

    @Autowired
    private BrandCarRepository brandCarRepository;

    @Override
    public BrandCar convert(String source) {
        if (source == null || source.isEmpty() || source.equals("0")) {
            return null;
        }
        try {
            int id = Integer.parseInt(source);
            return brandCarRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
