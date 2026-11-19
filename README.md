# IPTV Player – Android (telefon, tablet, Android TV)

Ten sam wygląd i funkcje co wersja na telewizor z Titan OS (plik `app/src/main/assets/index.html`),
uruchamiane w pełnoekranowym WebView. Obraz odtwarza sprzętowy odtwarzacz ExoPlayer (Media3) – szybki start kanałów HD/4K, obsługa HEVC i strumieni .ts.

Języki interfejsu: polski, rosyjski, niemiecki (wybór na ekranie startowym i w menu).

## Jak zbudować APK
**Bez instalowania czegokolwiek (GitHub):** wgraj zawartość tego folderu do repozytorium na GitHubie.
GitHub sam zbuduje aplikację – plik APK pobierzesz z zakładki **Actions** → najnowsze uruchomienie → **Artifacts**.

**W Android Studio:** File → Open → wybierz ten folder → Build → Build App Bundle(s) / APK(s) → Build APK(s).

## Zmiana wyglądu lub funkcji
Podmień plik `app/src/main/assets/index.html` na nowszą wersję aplikacji i zbuduj APK ponownie.
