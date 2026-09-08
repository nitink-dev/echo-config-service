package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.entity.SlideScanner;

import java.util.UUID;

public class SlideScannerTestData {

    public static SlideScanner scanner(String deviceSerialNumber) {
        SlideScanner scanner = new SlideScanner();
        scanner.setId(UUID.randomUUID().toString());
        scanner.setDeviceId(deviceSerialNumber);
        scanner.setDeviceSerialNumber(deviceSerialNumber);
        scanner.setName("SS12118");
        scanner.setModel("GT450DX");
        scanner.setLocation("Evanston");
        scanner.setDepartment("digital-pathology-dataset");
        scanner.setDicomStore("projects/p1/locations/l1/datasets/d1/dicomStores/store1");
        scanner.setAeTitle("SVS_STORE_SCP");
        scanner.setPort("1010");
        scanner.setHospitalName("Evanston Hospital");
        scanner.setIpAddress("10.0.0.10");
        scanner.setVendor("Acme");
        scanner.setResearch(Boolean.FALSE);
        scanner.setConnected(Boolean.TRUE);
        scanner.setRemoteAeTitle("SRORESCP");
        scanner.setRemoteHost("171.33.43.10");
        scanner.setRemotePort(9999);
        scanner.setStorageStrategy("C-STORE");
        return scanner;
    }
}
