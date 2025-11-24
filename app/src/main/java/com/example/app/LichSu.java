package com.example.app;

public class LichSu {
    private String viTri;
    private String ngayVao;
    private String gioVao;
    private String ngayRa;
    private String gioRa;
    private String bienSo;
    private String duongDanAnh;
    private String key; // Firebase key

    // Required empty constructor for Firebase
    public LichSu() {
    }

    // Constructor with parameters
    public LichSu(String viTri, String ngayVao, String gioVao, String ngayRa, String gioRa, String bienSo, String duongDanAnh) {
        this.viTri = viTri;
        this.ngayVao = ngayVao;
        this.gioVao = gioVao;
        this.ngayRa = ngayRa;
        this.gioRa = gioRa;
        this.bienSo = bienSo;
        this.duongDanAnh = duongDanAnh;
    }

    // Getters and Setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getViTri() {
        return viTri;
    }

    public void setViTri(String viTri) {
        this.viTri = viTri;
    }

    public String getNgayVao() {
        return ngayVao;
    }

    public void setNgayVao(String ngayVao) {
        this.ngayVao = ngayVao;
    }

    public String getGioVao() {
        return gioVao;
    }

    public void setGioVao(String gioVao) {
        this.gioVao = gioVao;
    }

    public String getNgayRa() {
        return ngayRa;
    }

    public void setNgayRa(String ngayRa) {
        this.ngayRa = ngayRa;
    }

    public String getGioRa() {
        return gioRa;
    }

    public void setGioRa(String gioRa) {
        this.gioRa = gioRa;
    }

    public String getBienSo() {
        return bienSo;
    }

    public void setBienSo(String bienSo) {
        this.bienSo = bienSo;
    }

    public String getDuongDanAnh() {
        return duongDanAnh;
    }

    public void setDuongDanAnh(String duongDanAnh) {
        this.duongDanAnh = duongDanAnh;
    }
}
