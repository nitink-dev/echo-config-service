package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.model.QaSlideDetails;

import java.util.List;
import java.util.UUID;

public class QaSlideTestData {

    public static final String MISSING_BARCODE = "unavailable-barcode";
    private static final String ACTIVATION_CODE = "Vsy6H0mbnuedkVATRrmhkji/DneagLfZEACPiNquNjOQQbRYLfdjGFYnVss=";

    public static QaSlide pathQaSlide() {
        return new QaSlide(UUID.randomUUID().toString(), "10224", "Elcwq81cA1dPXm7M");
    }

    public static List<QaSlide> listPathQaSlide() {
        return List.of(new QaSlide(UUID.randomUUID().toString(), "10224", ACTIVATION_CODE),
                new QaSlide(UUID.randomUUID().toString(), "10001", ACTIVATION_CODE));
    }

    public static QaSlideDetails qaSlideDetails() {
        return new QaSlideDetails("https://dicom.qa/path", listPathQaSlide());
    }

    public static QaSlide newQaSlide() {
        return new QaSlide(null, "10224", "Elcwq81cA1dPXm7M");
    }

    public static QaSlide slide(String id, String barcode, String activationCode) {
        return new QaSlide(id, barcode, activationCode);
    }
}
