// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Vietnamese (`vi`).
class AppLocalizationsVi extends AppLocalizations {
  AppLocalizationsVi([String locale = 'vi']) : super(locale);

  @override
  String get generalSettings => 'Cài đặt chung';

  @override
  String get aboutFlauncher => 'Giới thiệu Backdrop Launcher';

  @override
  String get addCategory => 'Thêm danh mục';

  @override
  String get alphabetical => 'Theo bảng chữ cái';

  @override
  String get appCardHighlightAnimation => 'Hiệu ứng làm nổi bật ô ứng dụng';

  @override
  String get showFocusBorders => 'Hiện viền ô đang chọn';

  @override
  String get focusBorderThickness => 'Độ dày viền ô đang chọn';

  @override
  String get focusBorderThin => 'Mảnh (1,0 dp)';

  @override
  String get focusBorderDefault => 'Mặc định (1,5 dp)';

  @override
  String get focusBorderMedium => 'Vừa (2,0 dp)';

  @override
  String get focusBorderBold => 'Dày (3,0 dp)';

  @override
  String get appInfo => 'Thông tin ứng dụng';

  @override
  String get appKeyClick => 'Âm thanh khi bấm phím';

  @override
  String get appearanceSettings => 'Hiển thị';

  @override
  String get backgroundBlur => 'Làm mờ hình nền';

  @override
  String get applications => 'Ứng dụng';

  @override
  String get autoHideAppBar => 'Tự động ẩn thanh trạng thái';

  @override
  String get backButtonAction => 'Chức năng nút Quay lại';

  @override
  String get category => 'Danh mục';

  @override
  String get categories => 'Danh mục';

  @override
  String get columnCount => 'Số cột';

  @override
  String get date => 'Ngày';

  @override
  String get dateAndTimeFormat => 'Định dạng ngày và giờ';

  @override
  String get delete => 'Xóa';

  @override
  String get dialogOptionBackButtonActionDoNothing => 'Không làm gì';

  @override
  String get dialogOptionBackButtonActionShowScreensaver =>
      'Mở trình bảo vệ màn hình';

  @override
  String get dialogOptionBackButtonActionShowClock => 'Hiện đồng hồ';

  @override
  String get dialogTextNoFileExplorer =>
      'Vui lòng cài ứng dụng quản lý tệp để chọn ảnh.';

  @override
  String get dialogTitleBackButtonAction => 'Chọn chức năng cho nút Quay lại';

  @override
  String disambiguateCategoryTitle(String title) {
    return '$title (Danh mục)';
  }

  @override
  String formattedDate(String dateString) {
    return 'Ngày theo định dạng: $dateString';
  }

  @override
  String formattedTime(String timeString) {
    return 'Giờ theo định dạng: $timeString';
  }

  @override
  String get gradient => 'Màu chuyển sắc';

  @override
  String get height => 'Chiều cao';

  @override
  String get hide => 'Ẩn';

  @override
  String get hiddenApplications => 'Ứng dụng đã ẩn';

  @override
  String get layout => 'Bố cục';

  @override
  String get loading => 'Đang tải';

  @override
  String get manual => 'Thủ công';

  @override
  String get mustNotBeEmpty => 'Không được để trống';

  @override
  String get name => 'Tên';

  @override
  String get noDateFormatSpecified => 'Chưa chọn định dạng ngày';

  @override
  String get noTimeFormatSpecified => 'Chưa chọn định dạng giờ';

  @override
  String get allApplications => 'Tất cả ứng dụng';

  @override
  String get nonTvApplications => 'Ứng dụng không dành cho TV';

  @override
  String get open => 'Mở';

  @override
  String get orSelectFormatSpecifiers => 'Hoặc chọn các thành phần định dạng';

  @override
  String removeFrom(String name) {
    return 'Xóa khỏi $name';
  }

  @override
  String get renameCategory => 'Đổi tên danh mục';

  @override
  String get reorder => 'Sắp xếp lại';

  @override
  String get row => 'Hàng';

  @override
  String get rowHeight => 'Chiều cao hàng';

  @override
  String get save => 'Lưu';

  @override
  String get statusBar => 'Thanh trạng thái';

  @override
  String get settings => 'Cài đặt';

  @override
  String get show => 'Hiện';

  @override
  String get showCategoryTitles => 'Hiện tên danh mục';

  @override
  String get sort => 'Sắp xếp';

  @override
  String get systemSettings => 'Cài đặt hệ thống';

  @override
  String get updateCheck => 'Kiểm tra cập nhật';

  @override
  String get updateNoUpdateTitle => 'Chưa có bản cập nhật';

  @override
  String updateNoUpdateBody(String currentVersion) {
    return 'Bạn đang dùng phiên bản mới nhất ($currentVersion).';
  }

  @override
  String get updateAvailableTitle => 'Có bản cập nhật';

  @override
  String updateAvailableBody(String latestVersion, String currentVersion) {
    return 'Đã có phiên bản $latestVersion (hiện tại: $currentVersion).';
  }

  @override
  String get updateChangelog => 'Có gì mới';

  @override
  String get updateDownloadButton => 'Tải xuống';

  @override
  String get updateReadyToInstallTitle => 'Sẵn sàng cài đặt';

  @override
  String updateReadyToInstallBody(String latestVersion) {
    return 'Đã tải APK phiên bản $latestVersion. Bạn muốn cài đặt ngay không?';
  }

  @override
  String get updateInstallButton => 'Cài đặt';

  @override
  String get updateInstallPermissionTitle => 'Cần quyền cài đặt ứng dụng';

  @override
  String get updateInstallPermissionBody =>
      'Cho phép Backdrop Launcher cài ứng dụng không rõ nguồn gốc, rồi thử cập nhật lại.';

  @override
  String get updateOpenPermissionSettingsButton => 'Mở cài đặt quyền';

  @override
  String get updateErrorGeneric =>
      'Không thể kiểm tra cập nhật. Vui lòng thử lại.';

  @override
  String textAboutDialog(String repoUrl) {
    return 'Backdrop Launcher là ứng dụng màn hình chính mã nguồn mở dành cho Android TV, được tùy chỉnh từ FLauncher.\n\nĐược John Neerdael tùy chỉnh. Dựa trên Arc Launcher, LTvLauncher và FLauncher.\nMã nguồn tại $repoUrl.';
  }

  @override
  String get textEmptyCategory => 'Danh mục này đang trống.';

  @override
  String get time => 'Giờ';

  @override
  String get titleStatusBarSettingsPage =>
      'Chọn nội dung hiển thị trên thanh trạng thái';

  @override
  String get tvApplications => 'Ứng dụng dành cho TV';

  @override
  String get type => 'Loại';

  @override
  String get typeInTheDateFormat => 'Nhập định dạng ngày';

  @override
  String get typeInTheHourFormat => 'Nhập định dạng giờ';

  @override
  String get uninstall => 'Gỡ cài đặt';

  @override
  String get wallpaper => 'Hình nền';

  @override
  String get withEllipsisAddTo => 'Thêm vào...';

  @override
  String get watchNextSectionTitle => 'Xem tiếp';

  @override
  String get showWatchNextSection => 'Hiện mục Xem tiếp';

  @override
  String get watchNextPermissionTitle => 'Cần cấp quyền cho mục Xem tiếp';

  @override
  String get watchNextPermissionBody =>
      'Cho phép truy cập danh sách chương trình TV để hiện những nội dung bạn đang xem dở.';

  @override
  String get watchNextGrantPermission => 'Cấp quyền';

  @override
  String get watchNextCheckPermission => 'Kiểm tra lại';

  @override
  String get interface => 'Giao diện';

  @override
  String get system => 'Hệ thống';

  @override
  String get accentColor => 'Màu nhấn';

  @override
  String get miscellaneous => 'Tùy chọn khác';

  @override
  String get screensaverSettings => 'Cài đặt trình bảo vệ màn hình';

  @override
  String get screensaverClockStyle => 'Kiểu đồng hồ bảo vệ màn hình';

  @override
  String get showAppNamesBelowIcons => 'Hiện tên ứng dụng dưới biểu tượng';

  @override
  String get networkIndicator => 'Trạng thái mạng';

  @override
  String get customName => 'Tên tùy chỉnh';

  @override
  String get lastUsed => 'Dùng gần đây';

  @override
  String get watchNextBigPictureMode => 'Chế độ chìm đắm';

  @override
  String get watchNextBigPictureModeDescription =>
      'Hiển thị hình nền nổi bật toàn màn hình với nút xem tác vụ';

  @override
  String get watchNextWatch => 'Xem';

  @override
  String get watchNextResume => 'Tiếp tục xem';

  @override
  String get watchNextRefresh => 'Làm mới nội dung';

  @override
  String get watchNextRefreshDescription =>
      'Tải lại đề xuất từ các ứng dụng đa phương tiện';

  @override
  String get watchNextRefreshed => 'Đã làm mới nội dung Tiếp tục xem';

  @override
  String get watchNextFilterApps => 'Lọc ứng dụng';

  @override
  String get watchNextFilterAppsDescription =>
      'Chọn ứng dụng có thể hiển thị nội dung trong Tiếp tục xem';

  @override
  String get watchNextNoApps =>
      'Hiện không có ứng dụng truyền thông nào cung cấp nội dung Tiếp tục xem';

  @override
  String get watchNextShowAllApps => 'Hiển thị tất cả ứng dụng đã cài đặt';

  @override
  String get watchNextHideWithoutImages => 'Ẩn chương trình không có hình ảnh';

  @override
  String get watchNextHideWithoutImagesDescription =>
      'Chỉ hiển thị các chương trình có hình ảnh áp phích hoặc hình nền';

  @override
  String get watchNextMaxItems => 'Số lượng chương trình tối đa';

  @override
  String get watchNextMaxItemsDescription =>
      'Giới hạn số lượng chương trình hiển thị trong Tiếp tục xem';

  @override
  String get watchNextMaxItemsUnlimited => 'Không giới hạn';

  @override
  String get addToCategory => 'Thêm vào danh mục';

  @override
  String get setCustomBanner => 'Đặt ảnh đại diện tùy chỉnh';

  @override
  String get clearCustomBanner => 'Xóa ảnh đại diện tùy chỉnh';

  @override
  String customBannerSetFailed(String error) {
    return 'Không thể đặt ảnh đại diện: $error';
  }

  @override
  String customBannerClearFailed(String error) {
    return 'Không thể xóa ảnh đại diện: $error';
  }

  @override
  String get preview => 'Xem trước';

  @override
  String get colorPurple => 'Tím';

  @override
  String get colorTeal => 'Xanh cổ vịt';

  @override
  String get colorBlue => 'Xanh dương';

  @override
  String get colorOrange => 'Cam';

  @override
  String get colorPink => 'Hồng';

  @override
  String get colorGreen => 'Xanh lá';

  @override
  String get colorWhite => 'Trắng';

  @override
  String get colorYellow => 'Vàng';

  @override
  String get colorRed => 'Đỏ';

  @override
  String get colorCyan => 'Xanh lơ';

  @override
  String get colorIndigo => 'Chàm';

  @override
  String get colorLime => 'Xanh nõn chuối';

  @override
  String get colorAmber => 'Hổ phách';

  @override
  String get colorRose => 'Hồng phấn';

  @override
  String get colorIceBlue => 'Xanh băng';

  @override
  String get clockStyleMinimal => 'Tối giản';

  @override
  String get clockStyleMinimalDescription => 'Chữ mảnh, thanh lịch (mặc định)';

  @override
  String get clockStyleBold => 'Chữ đậm';

  @override
  String get clockStyleBoldDescription => 'Chữ dày, dễ nhìn';

  @override
  String get clockStyleRetro => 'Hoài cổ';

  @override
  String get clockStyleRetroDescription =>
      'Chữ đơn cách, kiểu màn hình máy tính cổ';

  @override
  String get clockStyleElegant => 'Thanh lịch';

  @override
  String get clockStyleElegantDescription => 'Kiểu chữ có chân cổ điển';

  @override
  String get clockStyleNeon => 'Neon';

  @override
  String get clockStyleNeonDescription =>
      'Chữ siêu mảnh với hiệu ứng phát sáng';

  @override
  String get clockStylePixel => 'Điểm ảnh';

  @override
  String get clockStylePixelDescription =>
      'Chữ đơn cách đậm, phong cách trò chơi cổ';

  @override
  String get clockStyleDigital => 'Kỹ thuật số';

  @override
  String get clockStyleDigitalDescription => 'Chữ đơn cách rõ ràng';

  @override
  String get updateNoReleaseTitle => 'Chưa có bản phát hành';

  @override
  String get updateNoReleaseBody =>
      'Chưa có bản phát hành ổn định với APK có thể cài đặt. Vui lòng thử lại sau.';
}
