package com.project.CarRental2.model;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Car {
	public Car(int id_car) {
		this.idCar=id_car;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int idCar;

	@Column(columnDefinition = "nvarchar(50) not null")
	private String nameCar;

	@Column(columnDefinition = "nvarchar(4000)")
	private String overviewCar;

	private int price;
	private int promotionalPrice;
	
	@Column(columnDefinition = "int default 0")
	private int oldPromotionalPrice;

	@Column(columnDefinition = "nvarchar(20)", unique = true)
	private String licensePlates;

	private int modelYear;
	private int status;
	private boolean fuel; // true is gasoline flase is desen;
	// fuel for 100km
	private float fuelFor100km;
	// Quãng đường giao xe tối đa
	private int maximumDeliveryDistance;
	private int deliveryFeeFor1Km;

	// km tối đa trên ngày
	private int maximumKilometersperDay;
	private int limitCrossingfee1Km;

	@Column(columnDefinition = "nvarchar(2000)")
	private String polyUseCar;

	@Column(columnDefinition = "nvarchar(1000)")
	private String addressCar;

	@Column(columnDefinition = "nvarchar(200)")
	private String avatarCar;

	@Column(columnDefinition = "nvarchar(2000)")
	private String imageCar;
	// số ghế ngồi
	private int numberOfSeats;

	private boolean bluetooth;
	// camerea hànhtrinhf
	private boolean dashCamera;
	// camera lùi
	private boolean reverseCamera;
	// camera 360
	private boolean camera360;
	// cam cập lề
	private boolean parkingCamera;
	// cảm biến ám suât lopps
	private boolean tpms;
	// cảnh báo tốc độ
	private boolean speedWarning;
	// gps
	private boolean gpsLocator;
	// Cửa sổ trời
	private boolean sunroof;
	// màn hình dvd
	private boolean dvdScreen;
	// tự lái hoặc có tài
	private boolean driver;
	// map
	private boolean maps;
	// ghế tre em
	private boolean babyseat;
	// lốp dự phòng
	private boolean spareTire;
	// usb
	private boolean usb;
	// cảnh báo va chạm
	private boolean impactSensor;
	// nắp thùng xe bán tải
	private boolean bonnet;
	// thu phí không dừng
	private boolean etc;
	// túi khí an toàn
	private boolean airbags;
	// số sàn hoặc tự dộng
	private boolean manualTransmissionCar;

	private Date createDate;
	private Date updateDate;


	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_brand")
	@JsonIgnore
	private BrandCar brandCar;
	
	@OneToMany(mappedBy = "car", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	@JsonIgnore
	private List<Booking> booking;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_user")
	@JsonIgnore
	private User user;


	@Transient
	@JsonProperty("displayAddress")
	public String getDisplayAddress() {
		if (addressCar == null)
			return "";
		String[] adrr = addressCar.split(",");
		if (adrr.length < 2)
			return addressCar;
		String newAddress = "";
		for (int i = adrr.length - 2; i < adrr.length; i++) {
			newAddress = newAddress + adrr[i].trim() + ", ";
		}
		if (newAddress.endsWith(", ")) {
			newAddress = newAddress.substring(0, newAddress.length() - 2);
		}
		return newAddress;
	}

	@Transient
	@JsonProperty("finalPrice")
	public int getFinalPrice() {
		if (promotionalPrice > 0) {
			return price - (price * promotionalPrice / 100);
		}
		return price;
	}

}
