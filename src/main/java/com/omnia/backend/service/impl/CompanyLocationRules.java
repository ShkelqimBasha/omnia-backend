package com.omnia.backend.service.impl;
public final class CompanyLocationRules {
    private CompanyLocationRules(){}
    public static boolean validPair(Double lat,Double lon){
        if(lat==null&&lon==null)return true;
        return lat!=null&&lon!=null&&Double.isFinite(lat)&&Double.isFinite(lon)
            &&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;
    }
}
