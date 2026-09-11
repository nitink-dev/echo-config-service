package com.eh.digiatalpathalogy.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.SLIDE_SCANNER;

@Document(collection = SLIDE_SCANNER)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlideScanner {

    @Id
    private String id;
    private String deviceSerialNumber;
    @NotBlank
    private String name;
    private String model;
    @NotBlank
    private String scannerType;
    @NotBlank
    private String location;
    @NotBlank
    private String department;
    private String dicomStore;
    @NotBlank
    private String aeTitle;
    private String port;
    private String hospitalName;
    private String ipAddress;
    private String vendor;
    private Boolean research;
    private Boolean connected;
    private String remoteAeTitle;
    private String remoteHost;
    private Integer remotePort;
    private String storageStrategy;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceSerialNumber() {
        return deviceSerialNumber;
    }

    public void setDeviceSerialNumber(String deviceSerialNumber) {
        this.deviceSerialNumber = deviceSerialNumber;
    }

    public @NotBlank String getName() {
        return name;
    }

    public void setName(@NotBlank String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(@NotBlank String model) {
        this.model = model;
    }

    public @NotBlank String getScannerType() {
        return scannerType;
    }

    public void setScannerType(@NotBlank String scannerType) {
        this.scannerType = scannerType;
    }

    public @NotBlank String getLocation() {
        return location;
    }

    public void setLocation(@NotBlank String location) {
        this.location = location;
    }

    public @NotBlank String getDepartment() {
        return department;
    }

    public void setDepartment(@NotBlank String department) {
        this.department = department;
    }

    public String getDicomStore() {
        return dicomStore;
    }

    public void setDicomStore(String dicomStore) {
        this.dicomStore = dicomStore;
    }

    public @NotBlank String getAeTitle() {
        return aeTitle;
    }

    public void setAeTitle(@NotBlank String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public Boolean getResearch() {
        return research;
    }

    public void setResearch(Boolean research) {
        this.research = research;
    }

    public Boolean getConnected() {
        return connected;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public String getRemoteAeTitle() {
        return remoteAeTitle;
    }

    public void setRemoteAeTitle(String remoteAeTitle) {
        this.remoteAeTitle = remoteAeTitle;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public Integer getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(Integer remotePort) {
        this.remotePort = remotePort;
    }

    public String getStorageStrategy() {
        return storageStrategy;
    }

    public void setStorageStrategy(String storageStrategy) {
        this.storageStrategy = storageStrategy;
    }
}