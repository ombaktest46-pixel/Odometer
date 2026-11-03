# MOTO ODO – Automatic GPS Odometer

Aplikasi Android native yang mengubah smartphone menjadi dashboard odometer dan speedometer digital sepeda motor dengan fitur **Auto Tracking GPS otomatis**, **Total Odometer permanen**, **notifikasi Foreground Service real-time**, **analitik statistik berkendara**, serta **peta rute vektor interaktif**.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Keputusan arsitektur dan fitur utama di bawah ini telah disesuaikan dengan jawaban klarifikasi Anda serta spesifikasi lengkap yang diminta.

- **Penyimpanan Lokal & Backup/Restore (Confirmed)**: Menggunakan **Jetpack DataStore** untuk menyimpan Total Odometer permanen, Trip Odometer aktif, serta preferensi pengguna agar tahan terhadap penutupan aplikasi maupun restart perangkat, dipadukan dengan **Room Database** untuk menyimpan riwayat perjalanan dan titik koordinat rute. Fitur **Backup & Restore** diimplementasikan melalui ekspor dan impor file JSON standar menggunakan Storage Access Framework (`CreateDocument` / `OpenDocument`) dengan proteksi konfirmasi ganda pada Total Odometer.
- **Visualisasi Rute Perjalanan (Confirmed)**: Menggunakan **Kanvas Rute Vektor Interaktif** berbasis Jetpack Compose `Canvas` yang menggambar jalur koordinat GPS perjalanan lengkap dengan titik awal (Start), titik akhir (Finish), gradien warna kecepatan sepanjang rute, kompas orientasi, dan kontrol zoom/pan tanpa ketergantungan pada API key eksternal.
- **Mode Simulasi Pengujian & GPS Asli (Confirmed)**: Menggunakan `FusedLocationProviderClient` dan sensor lokasi perangkat secara penuh untuk pemakaian nyata di jalan raya, ditambah **Mode Simulasi Berkendara (Safety Simulation Mode)** berindikator jelas agar seluruh alur Auto Tracking (mulai bergerak, akselerasi, lampu merah/berhenti, jalan kembali, hingga parkir), odometer, dan notifikasi live dapat diuji langsung ketika perangkat berada di dalam ruangan.

---

### 1. Overview & Core Concept

- **What It Does**: Memantau pergerakan sepeda motor secara otomatis di latar belakang maupun saat layar terkunci. Begitu pengguna menyalakan sakelar **Auto Tracking**, sistem mendeteksi kapan motor mulai melaju, mencatat penambahan jarak ke **Total Odometer** (permanen) dan **Trip Odometer**, menghitung kecepatan real-time/rata-rata/maksimal, memperbarui notifikasi live secara terus-menerus, dan otomatis menyelesaikan serta menyimpan sesi perjalanan ketika motor berhenti parkir.
- **Target Audience / Persona**: Pengendara sepeda motor harian, pelaju (commuter), penghobi touring, maupun pemilik motor modifikasi/klasik yang membutuhkan odometer dan speedometer digital presisi tinggi dengan pencatatan riwayat perjalanan otomatis.
- **Key Value**: Pengalaman *"Set & Forget"* — pengendara tidak perlu menekan tombol START/STOP setiap kali berkendara. Total Odometer terjamin permanen dan terpisah dari penghapusan riwayat perjalanan, dilengkapi filter anti-GPS drift agar angka kilometer tidak bertambah saat motor terparkir.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Aktivasi Auto Tracking & Izin Bertahap**: Pengguna menekan tombol **AKTIFKAN AUTO TRACKING** di Dashboard -> Sistem memeriksa izin lokasi presisi (`ACCESS_FINE_LOCATION`), izin notifikasi (`POST_NOTIFICATIONS` untuk Android 13+), dan panduan izin lokasi latar belakang (`ACCESS_BACKGROUND_LOCATION`) serta informasi optimasi baterai -> `Foreground Location Service` aktif dan menampilkan notifikasi permanen **MOTO ODO 🏍️**.
  2. **Deteksi Berkendara Otomatis (Auto-Move & Auto-Pause)**: Saat motor mulai melaju melewati ambang batas kecepatan dan perpindahan koordinat yang valid, status berubah menjadi `● MOTOR BERJALAN`, durasi berjalan, speedometer menampilkan kecepatan live, dan odometer bertambah. Ketika berhenti di lampu merah atau parkir, kecepatan otomatis turun ke `0 KM/JAM`, status berubah menjadi `● MOTOR BERHENTI`, dan sesi perjalanan disimpan ke riwayat setelah durasi berhenti melewati batas parkir.
  3. **Inspeksi Riwayat & Kanvas Rute**: Pengguna membuka tab **Riwayat** -> Melihat daftar perjalanan otomatis lengkap (tanggal, jam mulai-selesai, jarak, durasi, kecepatan rata-rata & maks) -> Mengetuk salah satu item membuka dialog/layar detail perjalanan dengan **Kanvas Rute Vektor Interaktif** yang memvisualisasikan lintasan GPS dan profil kecepatan.
  4. **Analitik Statistik & Kalibrasi**: Pengguna membuka tab **Statistik** untuk memantau akumulasi kilometer Hari Ini, Minggu Ini, Bulan Ini, dan Keseluruhan beserta grafik batang interaktif harian & bulanan. Di tab **Pengaturan**, pengguna dapat mengatur sensitivitas gerak, filter akurasi GPS, faktor kalibrasi odometer (misal `0.90x` – `1.10x` atau input nilai odometer awal motor), serta melakukan Backup/Restore JSON.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Cyber-Tactical Digital Motorcycle Cluster** — terinspirasi dari panel instrumen TFT motor sport modern dengan kontras tinggi agar mudah dibaca di bawah sinar matahari maupun malam hari.
  - *Color Palette & Mood*:
    - Background Utama (`--background`): `#07090C` (Carbon Pitch Black)
    - Panel Instrumen (`--surface` / `--card`): `#11161D` dengan border halus `#1E2936`
    - Aksen Utama (`--primary`): `#00FF66` (Neon Telemetry Green untuk angka speedometer, indikator motor berjalan, dan tombol aktif)
    - Aksen Sekunder (`--secondary`): `#00E5FF` (Cyber Cyan untuk metrik Trip & rata-rata)
    - Indikator Peringatan / Berhenti (`--warning` / `--error`): `#FFB300` (Amber) untuk GPS lemah / motor berhenti, `#FF3B5C` (Crimson) untuk tindakan destruktif/reset.
  - *Typography & Hierarchy*:
    - Font Display Angka & Odometer: **Orbitron** / **Chakra Petch** (diunduh lokal melalui `font-util`) dengan karakter tabular/geometris tegas untuk pembacaan cepat angka kecepatan besar (`72sp–88sp`) dan digit ruang odometer.
    - Font UI & Label Teknis: **Space Grotesk** / **JetBrains Mono** untuk label metrik, satuan `KM/JAM`, koordinat, dan timestamp.
  - *Component Styling & Layout*:
    - Kartu odometer bergaya *rolling digital drum / segmented telemetry框* dengan pemisah desimal berwarna kontras.
    - Busur takometer/speedometer dinamis (`Canvas` arc gauge) yang mengelilingi angka kecepatan utama dan berubah intensitas warnanya seiring kenaikan kecepatan.
    - Navigasi bawah Material 3 (`NavigationBar`) 4 menu: **Dashboard**, **Riwayat**, **Statistik**, **Pengaturan** dengan indikator pil hijau neon dan dukungan penuh `WindowInsets`.

- **Interactive Feedback & Motion**:
  - Animasi transisi angka kecepatan halus (`animateFloatAsState` / `spring`), indikator denyut radar (`InfiniteTransition` pulse) saat `MOTOR BERJALAN` atau mencari sinyal GPS, serta dialog konfirmasi berlapis untuk aksi sensitif (Reset Trip & Proteksi Total Odometer).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Algoritma Deteksi Pergerakan & Anti-GPS Drift**
  - *Chosen Approach*: Menggabungkan validasi multi-parameter pada setiap pembaruan lokasi: (1) Filter akurasi horizontal (`location.accuracy <= maxAllowedAccuracyMeters`, default 20m), (2) Validasi kecepatan efektif (kombinasi `location.speed` dari Doppler GPS dan perhitungan jarak/waktu $\Delta d / \Delta t$ dengan batas kecepatan minimum berkendara sesuai sensitivitas, default `>= 3.5 km/jam`), (3) Filter lonjakan tidak masuk akal (*anti-teleport spike filter* jika kecepatan kalkulasi $> 220\text{ km/jam}$), dan (4) *Moving Window Confirmation* (membutuhkan minimal 2 sampel bergerak berturut-turut untuk memicu status `MOTOR BERJALAN` dan timeout idle untuk kembali ke `MOTOR BERHENTI` serta `0 KM/JAM` otomatis jika tidak ada pergerakan).
  - *Why*: Mencegah bertambahnya angka kilometer secara palsu saat motor terparkir di garasi/gedung akibat pantulan sinyal GPS (*multipath drift*), sekaligus memastikan respons cepat saat motor benar-benar mulai melaju.

- **Decision 2: Pemisahan Penyimpanan Total Odometer Permanen dan Riwayat Perjalanan**
  - *Chosen Approach*: Menyimpan `totalOdometerKm` dan `currentTripKm` secara atomik di **Jetpack DataStore** setiap kali ada penambahan jarak valid, sedangkan daftar sesi perjalanan disimpan di **Room Database** (`TripRecordEntity` + titik rute JSON/relasional).
  - *Why*: Menjamin **Total Odometer tidak pernah kembali ke nol** setiap berganti hari, tetap bertahan saat aplikasi ditutup atau HP di-restart, dan **sama sekali tidak terpengaruh** meskipun pengguna menghapus satu atau seluruh riwayat perjalanan di tab Riwayat.

- **Decision 3: Siklus Hidup Foreground Service & Pemulihan Otomatis**
  - *Chosen Approach*: Menggunakan `Service` dengan `foregroundServiceType="location"`, penguncian `START_STICKY`, saluran notifikasi prioritas rendah-sedang tanpa suara mengganggu (`IMPORTANCE_LOW` agar tidak berbunyi setiap detik saat angka berubah namun tetap tampil permanen di bilah status dan layar kunci), serta `BootCompletedReceiver` (`RECEIVE_BOOT_COMPLETED`) yang memeriksa status `isAutoTrackingEnabled` dan memulihkan pemantauan jika diizinkan oleh sistem Android.
  - *Why*: Memenuhi kebijakan ketat Android 10 hingga Android 15/16 terkait lokasi latar belakang dan memberikan transparansi penuh kepada pengguna mengenai pengaturan penghemat baterai perangkat.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose)                          │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐ │
│  │  Dashboard   │   │   Riwayat    │   │  Statistik   │   │  Pengaturan  │ │
│  │   Screen     │   │ + Peta Rute  │   │ + Grafik Bar │   │ + Kalibrasi  │ │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘ │
│         └──────────────────┴─────────┬────────┴──────────────────┘         │
│                                      ▼                                     │
│                         ┌─────────────────────────┐                        │
│                         │   MotoOdoViewModel      │                        │
│                         │ (StateFlow UI & Events) │                        │
│                         └────────────┬────────────┘                        │
└──────────────────────────────────────┼─────────────────────────────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         ▼                             ▼                             ▼
┌──────────────────────┐   ┌───────────────────────┐   ┌─────────────────────┐
│ AutoTrackingService  │   │ OdometerPreferences   │   │   TripRepository    │
│ (Foreground Location │──▶│ (Jetpack DataStore)   │──▶│  (Room Database +   │
│  + Live Notification)│   │ • Total Odometer (KM) │   │   JSON Backup/Rest) │
└──────────┬───────────┘   │ • Trip Odometer (KM)  │   │ • TripHistory Table │
           │               │ • Kalibrasi & Sensitiv│   │ • RoutePoints List  │
           ▼               └───────────────────────┘   └─────────────────────┘
┌──────────────────────┐
│ Motion & GPS Engine  │
│ • FusedLocationClient│
│ • Anti-Drift Filter  │
│ • Indoor Ride Sim    │
└──────────────────────┘
```

- **Data Model & State**:
  - **DataStore (`OdometerPreferences`)**:
    - `totalOdometerMeters: Double` (permanen, hanya bertambah atau diubah lewat fitur Kalibrasi/ Set Odometer Awal dengan konfirmasi keamanan).
    - `tripOdometerMeters: Double`, `tripStartTimeMillis: Long`, `tripMovingDurationMillis: Long`, `tripMaxSpeedKmh: Float`.
    - `isAutoTrackingEnabled: Boolean`, `sensitivityLevel: SensitivityLevel (LOW, MEDIUM, HIGH)`, `gpsAccuracyThresholdMeters: Float (10m, 20m, 35m)`, `calibrationFactor: Float (0.80f..1.20f)`, `speedUnit: SpeedUnit (KMH, MPH)`, `updateIntervalMs: Long (hemat baterai vs presisi tinggi)`.
  - **Room Database (`MotoOdoDatabase`)**:
    - `TripEntity`: `id`, `startTimeMillis`, `endTimeMillis`, `distanceKm`, `durationSeconds`, `avgSpeedKmh`, `maxSpeedKmh`, `startAddressLabel`, `routePointsJson` (daftar koordinat `Lat`, `Lng`, `SpeedKmh`, `Timestamp` terkompresi untuk digambar pada Kanvas Rute Vektor).
  - **Live Telemetry State (`TelemetryStateHolder` Singleton)**:
    - Menghubungkan `AutoTrackingService` secara reaktif dengan `MotoOdoViewModel` menggunakan `StateFlow` sehingga angka di Dashboard dan angka pada Notifikasi Background selalu sinkron 100% setiap detik.

- **Interactive Component & State Mapping**:
  - **Notifikasi Permanen (`MOTO ODO 🏍️`)**: Memformat tampilan multi-baris (`BigTextStyle`) yang memuat `TOTAL ODOMETER`, `TRIP`, `KECEPATAN`, dan `STATUS (● MOTOR BERJALAN / ● MOTOR BERHENTI / ● MENCARI GPS)`, dilengkapi 2 tombol aksi langsung: **Buka Aplikasi** (`PendingIntent` ke `MainActivity`) dan **Hentikan Auto Tracking** (`PendingIntent` ke `Service` dengan action `ACTION_STOP_TRACKING`).
  - **Deteksi Otomatis Perjalanan Baru**: Ketika status beralih dari `BERHENTI` ke `BERJALAN`, sistem otomatis membuka sesi perjalanan aktif di memori & DataStore. Ketika motor berhenti selama durasi parkir (misal 60 detik tanpa pergerakan atau ketika Auto Tracking dimatikan), sesi perjalanan dengan jarak $\ge 0.05\text{ KM}$ otomatis disimpan ke Room Database tanpa intervensi pengguna.
  - **Backup & Restore JSON**: Mengekspor seluruh nilai Total Odometer, Trip aktif, pengaturan kalibrasi, dan seluruh tabel riwayat perjalanan ke dalam satu berkas `.json` terstruktur, serta memvalidasi integritas data saat proses Restore.
