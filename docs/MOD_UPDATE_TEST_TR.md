# Mod Loader — mod güncelleme test APK’sı

Bu APK, `update.json` üzerinden elle güncelleme kontrolünü ve SHA-256 doğrulanan ZIP ile sürüm değiştirmeyi içerir. Android 11 veya üstü gerekir. Debug uygulama kimliği `dev.kohlrabenschwarz.ml.debug` olduğu için ana sürümden ayrı kurulabilir; ancak Shizuku üzerinden oyundaki aynı `Bundles/mods` alanını yönetir. İki uygulamada aynı anda mod işlemi başlatma.

## Telefon testi

1. APK’yı kur. Shizuku içinden test uygulamasına da izin ver. Uygulama dili olarak Türkçe seçebilirsin.
2. Güncelleme bilgisi olmayan mevcut bir ZIP’i içe aktar: önceki işlemler çalışmalı.
3. Güncelleme destekli versionCode 1 paketini içe aktar. **Tap for details / Ayrıntılar** ile kartı aç; **Mod güncellemesini kontrol et** ayrıntılar bölümünde görünmeli.
4. Aynı modId ve manifestUrl kullanan versionCode 2 ZIP’ini ve manifestini herkese açık HTTPS adresine yükle.
5. Eski karttan kontrol et. Yeni sürüm görünmeli. **İndir ve modu güncelle** düğmesi değişiklik açıklamasını ve kaynağı gösteren bir onay açar. İptal, mevcut modu değiştirmez.
6. Güncelle: kartın kimliği korunmalı, sürüm değişmeli, yeni sürüm pasif kalmalı.
7. Gerçek ve uyumlu bir modla aktif güncellemeyi dene: eski modun orijinalleri geri yüklenmeli, yeni sürümden çıkarılan eski dosyalar temizlenmeli. Yeni sürümü kendin etkinleştir.
8. Manifestte hash’i veya boyutu yanlış ver: indirme reddedilmeli ve mevcut ZIP korunmalı. Farklı modId, sürüm veya kaynak adresi de reddedilmeli.
9. İndirme sırasında bağlantıyı kesip yeniden dene. Eski arşiv değiştirilmemeli.

Eski arşiv ve geri alınmış yedekleri `Bundles/mods/<mod-klasörü>/previous/` içinde tutulur. Bir sonraki güncelleme yalnızca hemen önceki sürümü saklar. Bu klasör otomatik geri dönüş düğmesi değildir; eski ZIP gerekirse normal içe aktarma ile tekrar kullanılabilir. Modu silmek saklanan geçmişi de siler.

## Dosyadan veya linkten mod ekleme

**+** düğmesi yukarı doğru iki yuvarlak seçenek açar. **Dosyadan ekle**, mevcut dosya seçiciyi açar. **Linkten ekle**, doğrudan herkese açık HTTPS ZIP indirme adresini ister; ardından indirme ilerlemesini gösterir. Bir paylaşım sayfası veya giriş gerektiren bağlantı doğrudan ZIP adresi yerine geçmez. Menünün dışına dokunmak veya geri tuşu menüyü kapatır.

Linkten gelen ZIP de boyut sınırı, metadata, CRC ve oyun dosyası kontrollerinden geçer. Yarım indirmeler temizlenir. Aynı isim veya modId varsa aynı üzerine yazma onayı açılır; mod otomatik etkinleştirilmez. Dev Mode açıksa saklama sonrasında `.dev` çıktıları da hazırlanır.

## Kolay örnek dosyalar

Test kitindeki `sample-mod-v1.zip`, `sample-mod-v2.zip` ve `latest.json` dosyaları **örnek adresler** içerir; bu adreslerde yayın yapılmadı. Gerçek indirmenin denenmesi için kendi herkese açık HTTPS dizininle dosyaları yeniden üret:

```powershell
.\New-ModUpdateTestFixtures.ps1 -BaseUrl https://SENIN-ADRESIN/mod-test/ -OutputDirectory .\benim-testim
```

`benim-testim/sample-mod-v2.zip` ve `benim-testim/latest.json` dosyalarını bu dizine yükle. Telefonda yalnızca `benim-testim/sample-mod-v1.zip` dosyasını içe aktar; karttan güncellemeyi kontrol et. Adres bir indirme sayfası değil, doğrudan dosyanın HTTPS adresi olmalı. HTTP ve yerel ağ adresleri desteklenmez.

**Varsayılan örnek ZIP’ler gerçek oyun içeriği taşımaz. Gerçek oyunda etkinleştirme; sadece içe aktarma ve güncelleme akışını dene.** Aktif güncelleme için kendi uyumlu mod paketlerini kullan.

## Kendi modunu hazırlama

```powershell
.\New-ModUpdatePackage.ps1 `
  -InputZip .\modum.zip -OutputZip .\yayin\modum-1.0.0.zip `
  -ModId com.benim.modum -Version 1.0.0 -VersionCode 1 `
  -ManifestUrl https://SENIN-ADRESIN/modum/latest.json `
  -ZipUrl https://SENIN-ADRESIN/modum/modum-1.0.0.zip `
  -ManifestOutput .\yayin\v1-manifest.json
```

İkinci sürüm için yeni dosya yolları, `Version 1.1.0`, `VersionCode 2` ve `ManifestOutput .\yayin\latest.json` kullan. Aynı ModId ve ManifestUrl korunmalı. Araç eski dosyaları değiştirmez, yeni ZIP’in hash ve boyutunu hesaplar. Var olan çıktı dosyasını üzerine yazmaz.

ZIP’i yükledikten sonra `latest.json`ı yükle. Sonradan ZIP’i değiştirirsen hash ve boyutu da yeniden üretmen gerekir. SHA-256 indirilen dosyanın manifestle eşleşmesini doğrular; yayıncının kimliğini imzayla doğrulama bu test sürümünde yok.

## Güncelleme kaynağı okunamıyorsa

Hata kartındaki **Güncelleme kaynağı** satırı, içe aktarılan ZIP’in `update.json` dosyasında bulunan adresi gösterir. Sunucudaki `latest.json`ı düzenlemek bu adresi değiştirmez. Örnek `example.com` adresi görünüyorsa kendi adresinle yeniden üretilmiş v1 ZIP’ini içe aktar. Adresin sorgu parametreleri hata ekranında gösterilmez.

**Ayrıntı** satırı HTTP durumunu veya hata türünü gösterir: `HTTP 401` giriş gerektiğini, `HTTP 403` erişimin reddedildiğini, `HTTP 404` dosyanın bulunamadığını belirtir. `DNS`, `TLS`, `TIMEOUT` ve `CONNECTION` bağlantı aşamasını; `MANIFEST_JSON`, `MANIFEST_UTF8` ve `METADATA_OR_ADDRESS` içerik veya adres doğrulama sorununu ayırır. Hata sürerse bu iki satırı birlikte paylaş.

Cloudflare’daki dosyayı aynı telefonda gizli sekmeden açarak şifresiz erişimi kontrol et. FlareDrive’a tarayıcıda giriş yapılmış olması Android uygulamasına giriş sağlamaz.

## Dev Mode ve onaylı üzerine yazma

1. **Ayarlar → Dev Mode** seçeneğini aç. Yayın klasörünün kök HTTPS adresini girip kaydet; örneğin `https://kohlrabenschwarz.pages.dev/webdav/test/`.
2. Mod ekle. Shizuku bağlı olduğunda `Bundles/mods/<mod-klasörü>/.dev/` içinde `latest.json`, `zip-size.txt`, `zip-sha256.txt`, `checksums.json`, `info-update.json`, `update.json` ve `README.txt` oluşur. Mod kartının ayrıntılarında konum gösterilir. Dev Mode açılırken mevcut sağlıklı modlar için de dosyalar hazırlanır; bağlantı yoksa hazırlama bağlantı gelince yapılır.
3. Her mod için yayın kökünde **modId adlı ayrı bir altklasör** kullanılır. Örneğin `com.example.update-test/latest.json`. ZIP bağlantısı, mod klasöründeki gerçek saklanan ZIP adını kullanır. `.dev/README.txt` tam hedef adresleri gösterir; ZIP’i bu adla yükle, ardından ilgili `.dev/latest.json` dosyasını yükle.
4. `checksums.json` içindeki `readyToPublish` alanını kontrol et. Kimlik/sürüm veya `update.json` eksikse ya da kaynak adresi farklıysa çıktı taslaktır. `info-update.json` alanlarını mevcut ZIP’in `info.json` dosyasına ekle; `.dev/update.json`ı ZIP köküne koy. `info-update.json`, tam `info.json` yerine geçmez. Değiştirilmiş ZIP’i tekrar içe aktar; hash ve boyut yeni ZIP için otomatik hesaplanır.
5. Aynı isim (büyük/küçük harf farkı yok sayılır) **veya** aynı modId bulunursa çakışma onayı açılır. Birden fazla eşleşmede değiştirilecek kartı seç. **İptal** mevcut modları değiştirmez. **Üzerine yaz**, seçili kartın yerel kimliğini korur; farklı paket için eski aktif modun orijinalleri geri yüklenir, yeni paket pasif kalır ve önceki saklanan sürüm `previous/` altında tutulur. Elle üzerine yazma aynı sürüm numarasına izin verir; internetten güncelleme daha yüksek versionCode gerektirir.

`.dev` çıktıları ZIP’in içine eklenmez ve oyun dosyalarına uygulanmaz. Dev Mode kapatılınca mevcut çıktılar korunur. ZIP değişirse eski manifesti kullanma; yeniden içe aktarıp oluşan manifestle yayımla. Yayın adresini değiştirmek mevcut ZIP’in içindeki update.json’ı değiştirmez.
