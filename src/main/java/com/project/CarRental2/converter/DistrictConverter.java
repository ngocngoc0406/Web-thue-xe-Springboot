package com.project.CarRental2.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.project.CarRental2.model.District;
import com.project.CarRental2.repository.DistrictRepository;

@Component
public class DistrictConverter implements Converter<String, District> {

    @Autowired
    private DistrictRepository districtRepository;

    @Override
    public District convert(String source) {
        if (source == null || source.isEmpty() || source.equals("0")) {
            return null;
        }
        try {
            int id = Integer.parseInt(source);
            return districtRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
