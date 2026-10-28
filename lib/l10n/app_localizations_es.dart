// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get aboutFlauncher => 'Acerca de Backdrop Launcher';

  @override
  String get addCategory => 'Agregar categoría';

  @override
  String get alphabetical => 'Alfabético';

  @override
  String get appCardHighlightAnimation => 'Resaltar aplicaciones';

  @override
  String get showFocusBorders => 'Mostrar bordes de enfoque';

  @override
  String get focusBorderThickness => 'Grosor del borde de enfoque';

  @override
  String get focusBorderThin => 'Sutil (1.0 dp)';

  @override
  String get focusBorderDefault => 'Por defecto (1.5 dp)';

  @override
  String get focusBorderMedium => 'Medio (2.0 dp)';

  @override
  String get focusBorderBold => 'Grueso (3.0 dp)';

  @override
  String get appInfo => 'Datos de la aplicación';

  @override
  String get appKeyClick => 'Sonido al presionar una tecla';

  @override
  String get appearanceSettings => 'Apariencia';

  @override
  String get backgroundBlur => 'Desenfoque de fondo';

  @override
  String get applications => 'Aplicaciones';

  @override
  String get autoHideAppBar => 'Ocultar barra de estado automáticamente';

  @override
  String get backButtonAction => 'Acción del botón \'Atrás\'';

  @override
  String get category => 'Categoría';

  @override
  String get categories => 'Categorías';

  @override
  String get columnCount => 'Cantidad de columnas';

  @override
  String get date => 'Fecha';

  @override
  String get dateAndTimeFormat => 'Formato de fecha y hora';

  @override
  String get delete => 'Eliminar';

  @override
  String get dialogOptionBackButtonActionDoNothing => 'Nada';

  @override
  String get dialogOptionBackButtonActionShowScreensaver =>
      'Mostrar salvapantallas';

  @override
  String get dialogOptionBackButtonActionShowClock => 'Mostrar reloj';

  @override
  String get dialogTextNoFileExplorer =>
      'Por favor, instale un gestor de archivos para seleccionar una imagen.';

  @override
  String get dialogTitleBackButtonAction =>
      'Elegir la acción del botón \'Atrás\'';

  @override
  String disambiguateCategoryTitle(String title) {
    return '$title (Categoría)';
  }

  @override
  String formattedDate(String dateString) {
    return 'Fecha con formato: $dateString';
  }

  @override
  String formattedTime(String timeString) {
    return 'Hora con formato: $timeString';
  }

  @override
  String get gradient => 'Gradiente';

  @override
  String get height => 'Altura';

  @override
  String get hide => 'Ocultar';

  @override
  String get hiddenApplications => 'Aplicaciones ocultas';

  @override
  String get layout => 'Distribución';

  @override
  String get loading => 'Cargando';

  @override
  String get manual => 'Manual';

  @override
  String get mustNotBeEmpty => 'No debe estar vacío';

  @override
  String get name => 'Nombre';

  @override
  String get noDateFormatSpecified => 'Sin formato de fecha';

  @override
  String get noTimeFormatSpecified => 'Sin formato de hora';

  @override
  String get allApplications => 'Todas las aplicaciones';

  @override
  String get nonTvApplications => 'Otras aplicaciones';

  @override
  String get open => 'Abrir';

  @override
  String get orSelectFormatSpecifiers =>
      'O seleccione especificadores de formato';

  @override
  String removeFrom(String name) {
    return 'Remover de $name';
  }

  @override
  String get renameCategory => 'Renombrar categoría';

  @override
  String get reorder => 'Reordenar';

  @override
  String get row => 'Row';

  @override
  String get rowHeight => 'Row height';

  @override
  String get save => 'Guardar';

  @override
  String get statusBar => 'Barra de estado';

  @override
  String get settings => 'Ajustes';

  @override
  String get show => 'Mostrar';

  @override
  String get showCategoryTitles => 'Mostrar títulos de categorías';

  @override
  String get sort => 'Orden';

  @override
  String get systemSettings => 'Ajustes del sistema';

  @override
  String get updateCheck => 'Buscar actualizaciones';

  @override
  String get updateNoUpdateTitle => 'No hay actualizaciones';

  @override
  String updateNoUpdateBody(String currentVersion) {
    return 'Ya tienes la última versión ($currentVersion).';
  }

  @override
  String get updateAvailableTitle => 'Actualización disponible';

  @override
  String updateAvailableBody(String latestVersion, String currentVersion) {
    return 'La versión $latestVersion está disponible (actual: $currentVersion).';
  }

  @override
  String get updateChangelog => 'Novedades';

  @override
  String get updateDownloadButton => 'Descargar';

  @override
  String get updateReadyToInstallTitle => 'Lista para instalar';

  @override
  String updateReadyToInstallBody(String latestVersion) {
    return 'El APK de la versión $latestVersion ya se descargó. ¿Instalar ahora?';
  }

  @override
  String get updateInstallButton => 'Instalar';

  @override
  String get updateInstallPermissionTitle =>
      'Se requiere permiso de instalación';

  @override
  String get updateInstallPermissionBody =>
      'Permite que Backdrop Launcher instale apps desconocidas y vuelve a intentar la actualización.';

  @override
  String get updateOpenPermissionSettingsButton => 'Abrir ajustes de permisos';

  @override
  String get updateErrorGeneric =>
      'La actualización falló. Inténtalo de nuevo.';

  @override
  String textAboutDialog(String repoUrl) {
    return 'Backdrop Launcher es un lanzador de código abierto personalizado para Android TV, basado en FLauncher.\n\nPersonalizado por John Neerdael. Basado en Arc Launcher, LTvLauncher y FLauncher.\nCódigo fuente disponible en $repoUrl.';
  }

  @override
  String get textEmptyCategory => 'Esta categoría está vacía.';

  @override
  String get time => 'Hora';

  @override
  String get titleStatusBarSettingsPage =>
      'Elija la información a mostrar en la barra de estado';

  @override
  String get tvApplications => 'Aplicaciones del televisor';

  @override
  String get type => 'Tipo';

  @override
  String get typeInTheDateFormat => 'Escriba el formato de fecha';

  @override
  String get typeInTheHourFormat => 'Escriba el formato de hora';

  @override
  String get uninstall => 'Desinstalar';

  @override
  String get wallpaper => 'Fondo de pantalla';

  @override
  String get withEllipsisAddTo => 'Añadir a...';

  @override
  String get watchNextSectionTitle => 'Ver después';

  @override
  String get showWatchNextSection => 'Mostrar sección Ver después';

  @override
  String get watchNextPermissionTitle => 'Se requiere permiso para Ver después';

  @override
  String get watchNextPermissionBody =>
      'Permite el acceso a la guía de TV para mostrar lo que estabas viendo.';

  @override
  String get watchNextGrantPermission => 'Conceder permiso';

  @override
  String get watchNextCheckPermission => 'Comprobar de nuevo';

  @override
  String get interface => 'Interfaz';

  @override
  String get system => 'Sistema';

  @override
  String get accentColor => 'Color de acento';

  @override
  String get miscellaneous => 'Varios';

  @override
  String get screensaverSettings => 'Ajustes del salvapantallas';

  @override
  String get screensaverClockStyle => 'Estilo del reloj del salvapantallas';

  @override
  String get showAppNamesBelowIcons => 'Mostrar nombres bajo los iconos';

  @override
  String get networkIndicator => 'Indicador de red';

  @override
  String get customName => 'Nombre personalizado';

  @override
  String get lastUsed => 'Último uso';

  @override
  String get watchNextBigPictureMode => 'Modo inmersivo';

  @override
  String get watchNextBigPictureModeDescription =>
      'Mostrar fondo de pantalla completa con un botón de ver';

  @override
  String get watchNextWatch => 'Ver';

  @override
  String get watchNextResume => 'Reanudar';

  @override
  String get watchNextRefresh => 'Actualizar contenido';

  @override
  String get watchNextRefreshDescription =>
      'Recargar recomendaciones de los proveedores multimedia';

  @override
  String get watchNextRefreshed => 'Contenido de Seguir viendo actualizado';

  @override
  String get watchNextFilterApps => 'Filtrar aplicaciones';

  @override
  String get watchNextFilterAppsDescription =>
      'Selecciona qué aplicaciones pueden mostrar contenido en Seguir viendo';

  @override
  String get watchNextNoApps =>
      'Ninguna aplicación multimedia proporciona contenido actualmente';

  @override
  String get watchNextShowAllApps =>
      'Mostrar todas las aplicaciones instaladas';

  @override
  String get watchNextHideWithoutImages => 'Ocultar programas sin imagen';

  @override
  String get watchNextHideWithoutImagesDescription =>
      'Mostrar solo programas que incluyan póster o imagen de fondo';

  @override
  String get watchNextMaxItems => 'Máximo de programas';

  @override
  String get watchNextMaxItemsDescription =>
      'Limitar el número de programas mostrados en Seguir viendo';

  @override
  String get watchNextMaxItemsUnlimited => 'Ilimitado';

  @override
  String get addToCategory => 'Add to row';

  @override
  String get setCustomBanner => 'Set Custom Banner';

  @override
  String get clearCustomBanner => 'Clear Custom Banner';

  @override
  String customBannerSetFailed(String error) {
    return 'Failed to set banner: $error';
  }

  @override
  String customBannerClearFailed(String error) {
    return 'Failed to clear banner: $error';
  }

  @override
  String get preview => 'Preview';

  @override
  String get colorPurple => 'Purple';

  @override
  String get colorTeal => 'Teal';

  @override
  String get colorBlue => 'Blue';

  @override
  String get colorOrange => 'Orange';

  @override
  String get colorPink => 'Pink';

  @override
  String get colorGreen => 'Green';

  @override
  String get colorWhite => 'White';

  @override
  String get colorYellow => 'Yellow';

  @override
  String get colorRed => 'Red';

  @override
  String get colorCyan => 'Cyan';

  @override
  String get colorIndigo => 'Indigo';

  @override
  String get colorLime => 'Lime';

  @override
  String get colorAmber => 'Amber';

  @override
  String get colorRose => 'Rose';

  @override
  String get colorIceBlue => 'Ice Blue';

  @override
  String get clockStyleMinimal => 'Minimal';

  @override
  String get clockStyleMinimalDescription => 'Thin, elegant font (Default)';

  @override
  String get clockStyleBold => 'Bold';

  @override
  String get clockStyleBoldDescription => 'Thick, highly visible font';

  @override
  String get clockStyleRetro => 'Retro';

  @override
  String get clockStyleRetroDescription => 'Monospaced, retro terminal style';

  @override
  String get clockStyleElegant => 'Elegant';

  @override
  String get clockStyleElegantDescription => 'Classic serif typeface';

  @override
  String get clockStyleNeon => 'Neon';

  @override
  String get clockStyleNeonDescription => 'Ultra-thin, glowing style';

  @override
  String get clockStylePixel => 'Pixel';

  @override
  String get clockStylePixelDescription => 'Bold monospaced, arcade feel';

  @override
  String get clockStyleDigital => 'Digital';

  @override
  String get clockStyleDigitalDescription => 'Clean monospaced display';

  @override
  String get updateNoReleaseTitle => 'Aún no hay versiones disponibles';

  @override
  String get updateNoReleaseBody =>
      'Todavía no se ha publicado una versión estable con un APK instalable. Vuelve a intentarlo más tarde.';
}
