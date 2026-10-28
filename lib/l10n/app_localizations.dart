import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_es.dart';
import 'app_localizations_tr.dart';
import 'app_localizations_vi.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('es'),
    Locale('tr'),
    Locale('vi'),
  ];

  /// No description provided for @aboutFlauncher.
  ///
  /// In en, this message translates to:
  /// **'About Backdrop Launcher'**
  String get aboutFlauncher;

  /// No description provided for @addCategory.
  ///
  /// In en, this message translates to:
  /// **'Add category'**
  String get addCategory;

  /// No description provided for @alphabetical.
  ///
  /// In en, this message translates to:
  /// **'Alphabetical'**
  String get alphabetical;

  /// No description provided for @appCardHighlightAnimation.
  ///
  /// In en, this message translates to:
  /// **'App card highlight animation'**
  String get appCardHighlightAnimation;

  /// No description provided for @showFocusBorders.
  ///
  /// In en, this message translates to:
  /// **'Show focus borders'**
  String get showFocusBorders;

  /// No description provided for @focusBorderThickness.
  ///
  /// In en, this message translates to:
  /// **'Focus border thickness'**
  String get focusBorderThickness;

  /// No description provided for @focusBorderThin.
  ///
  /// In en, this message translates to:
  /// **'Subtle (1.0 dp)'**
  String get focusBorderThin;

  /// No description provided for @focusBorderDefault.
  ///
  /// In en, this message translates to:
  /// **'Default (1.5 dp)'**
  String get focusBorderDefault;

  /// No description provided for @focusBorderMedium.
  ///
  /// In en, this message translates to:
  /// **'Medium (2.0 dp)'**
  String get focusBorderMedium;

  /// No description provided for @focusBorderBold.
  ///
  /// In en, this message translates to:
  /// **'Bold (3.0 dp)'**
  String get focusBorderBold;

  /// No description provided for @appInfo.
  ///
  /// In en, this message translates to:
  /// **'Application info'**
  String get appInfo;

  /// No description provided for @appKeyClick.
  ///
  /// In en, this message translates to:
  /// **'Click sound on key press'**
  String get appKeyClick;

  /// No description provided for @appearanceSettings.
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get appearanceSettings;

  /// No description provided for @backgroundBlur.
  ///
  /// In en, this message translates to:
  /// **'Background blur'**
  String get backgroundBlur;

  /// No description provided for @applications.
  ///
  /// In en, this message translates to:
  /// **'Applications'**
  String get applications;

  /// No description provided for @autoHideAppBar.
  ///
  /// In en, this message translates to:
  /// **'Automatically hide status bar'**
  String get autoHideAppBar;

  /// No description provided for @backButtonAction.
  ///
  /// In en, this message translates to:
  /// **'Back button action'**
  String get backButtonAction;

  /// No description provided for @category.
  ///
  /// In en, this message translates to:
  /// **'Category'**
  String get category;

  /// No description provided for @categories.
  ///
  /// In en, this message translates to:
  /// **'Categories'**
  String get categories;

  /// No description provided for @columnCount.
  ///
  /// In en, this message translates to:
  /// **'Column count'**
  String get columnCount;

  /// No description provided for @date.
  ///
  /// In en, this message translates to:
  /// **'Date'**
  String get date;

  /// No description provided for @dateAndTimeFormat.
  ///
  /// In en, this message translates to:
  /// **'Date and time format'**
  String get dateAndTimeFormat;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// No description provided for @dialogOptionBackButtonActionDoNothing.
  ///
  /// In en, this message translates to:
  /// **'Do nothing'**
  String get dialogOptionBackButtonActionDoNothing;

  /// No description provided for @dialogOptionBackButtonActionShowScreensaver.
  ///
  /// In en, this message translates to:
  /// **'Show screensaver'**
  String get dialogOptionBackButtonActionShowScreensaver;

  /// No description provided for @dialogOptionBackButtonActionShowClock.
  ///
  /// In en, this message translates to:
  /// **'Show clock'**
  String get dialogOptionBackButtonActionShowClock;

  /// No description provided for @dialogTextNoFileExplorer.
  ///
  /// In en, this message translates to:
  /// **'Please install a file explorer in order to pick a picture.'**
  String get dialogTextNoFileExplorer;

  /// No description provided for @dialogTitleBackButtonAction.
  ///
  /// In en, this message translates to:
  /// **'Choose the back button action'**
  String get dialogTitleBackButtonAction;

  /// No description provided for @disambiguateCategoryTitle.
  ///
  /// In en, this message translates to:
  /// **'{title} (Category)'**
  String disambiguateCategoryTitle(String title);

  /// No description provided for @formattedDate.
  ///
  /// In en, this message translates to:
  /// **'Formatted date: {dateString}'**
  String formattedDate(String dateString);

  /// No description provided for @formattedTime.
  ///
  /// In en, this message translates to:
  /// **'Formatted time: {timeString}'**
  String formattedTime(String timeString);

  /// No description provided for @gradient.
  ///
  /// In en, this message translates to:
  /// **'Gradient'**
  String get gradient;

  /// No description provided for @height.
  ///
  /// In en, this message translates to:
  /// **'Height'**
  String get height;

  /// No description provided for @hide.
  ///
  /// In en, this message translates to:
  /// **'Hide'**
  String get hide;

  /// No description provided for @hiddenApplications.
  ///
  /// In en, this message translates to:
  /// **'Hidden Apps'**
  String get hiddenApplications;

  /// No description provided for @layout.
  ///
  /// In en, this message translates to:
  /// **'Layout'**
  String get layout;

  /// No description provided for @loading.
  ///
  /// In en, this message translates to:
  /// **'Loading'**
  String get loading;

  /// No description provided for @manual.
  ///
  /// In en, this message translates to:
  /// **'Manual'**
  String get manual;

  /// No description provided for @mustNotBeEmpty.
  ///
  /// In en, this message translates to:
  /// **'Must not be empty'**
  String get mustNotBeEmpty;

  /// No description provided for @name.
  ///
  /// In en, this message translates to:
  /// **'Name'**
  String get name;

  /// No description provided for @noDateFormatSpecified.
  ///
  /// In en, this message translates to:
  /// **'No date format specified'**
  String get noDateFormatSpecified;

  /// No description provided for @noTimeFormatSpecified.
  ///
  /// In en, this message translates to:
  /// **'No time format specified'**
  String get noTimeFormatSpecified;

  /// No description provided for @allApplications.
  ///
  /// In en, this message translates to:
  /// **'All Apps'**
  String get allApplications;

  /// No description provided for @nonTvApplications.
  ///
  /// In en, this message translates to:
  /// **'Non-TV Apps'**
  String get nonTvApplications;

  /// No description provided for @open.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get open;

  /// No description provided for @orSelectFormatSpecifiers.
  ///
  /// In en, this message translates to:
  /// **'Or select format specifiers'**
  String get orSelectFormatSpecifiers;

  /// No description provided for @removeFrom.
  ///
  /// In en, this message translates to:
  /// **'Remove from {name}'**
  String removeFrom(String name);

  /// No description provided for @renameCategory.
  ///
  /// In en, this message translates to:
  /// **'Rename category'**
  String get renameCategory;

  /// No description provided for @reorder.
  ///
  /// In en, this message translates to:
  /// **'Reorder'**
  String get reorder;

  /// No description provided for @row.
  ///
  /// In en, this message translates to:
  /// **'Row'**
  String get row;

  /// No description provided for @rowHeight.
  ///
  /// In en, this message translates to:
  /// **'Row height'**
  String get rowHeight;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// No description provided for @statusBar.
  ///
  /// In en, this message translates to:
  /// **'Status bar'**
  String get statusBar;

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// No description provided for @show.
  ///
  /// In en, this message translates to:
  /// **'Show'**
  String get show;

  /// No description provided for @showCategoryTitles.
  ///
  /// In en, this message translates to:
  /// **'Show category titles'**
  String get showCategoryTitles;

  /// No description provided for @sort.
  ///
  /// In en, this message translates to:
  /// **'Sort'**
  String get sort;

  /// No description provided for @systemSettings.
  ///
  /// In en, this message translates to:
  /// **'System settings'**
  String get systemSettings;

  /// No description provided for @updateCheck.
  ///
  /// In en, this message translates to:
  /// **'Check for updates'**
  String get updateCheck;

  /// No description provided for @updateNoUpdateTitle.
  ///
  /// In en, this message translates to:
  /// **'No updates available'**
  String get updateNoUpdateTitle;

  /// No description provided for @updateNoUpdateBody.
  ///
  /// In en, this message translates to:
  /// **'You are already on the latest version ({currentVersion}).'**
  String updateNoUpdateBody(String currentVersion);

  /// No description provided for @updateAvailableTitle.
  ///
  /// In en, this message translates to:
  /// **'Update available'**
  String get updateAvailableTitle;

  /// No description provided for @updateAvailableBody.
  ///
  /// In en, this message translates to:
  /// **'Version {latestVersion} is available (current: {currentVersion}).'**
  String updateAvailableBody(String latestVersion, String currentVersion);

  /// No description provided for @updateChangelog.
  ///
  /// In en, this message translates to:
  /// **'What\'s new'**
  String get updateChangelog;

  /// No description provided for @updateDownloadButton.
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get updateDownloadButton;

  /// No description provided for @updateReadyToInstallTitle.
  ///
  /// In en, this message translates to:
  /// **'Ready to install'**
  String get updateReadyToInstallTitle;

  /// No description provided for @updateReadyToInstallBody.
  ///
  /// In en, this message translates to:
  /// **'The APK for version {latestVersion} is downloaded. Start installation now?'**
  String updateReadyToInstallBody(String latestVersion);

  /// No description provided for @updateInstallButton.
  ///
  /// In en, this message translates to:
  /// **'Install'**
  String get updateInstallButton;

  /// No description provided for @updateInstallPermissionTitle.
  ///
  /// In en, this message translates to:
  /// **'Installer permission required'**
  String get updateInstallPermissionTitle;

  /// No description provided for @updateInstallPermissionBody.
  ///
  /// In en, this message translates to:
  /// **'Allow Backdrop Launcher to install unknown apps, then retry the update.'**
  String get updateInstallPermissionBody;

  /// No description provided for @updateOpenPermissionSettingsButton.
  ///
  /// In en, this message translates to:
  /// **'Open permission settings'**
  String get updateOpenPermissionSettingsButton;

  /// No description provided for @updateErrorGeneric.
  ///
  /// In en, this message translates to:
  /// **'Update check failed. Please try again.'**
  String get updateErrorGeneric;

  /// No description provided for @textAboutDialog.
  ///
  /// In en, this message translates to:
  /// **'Backdrop Launcher is an open-source Android TV launcher customized by John Neerdael. Based on Arc Launcher, LTvLauncher and FLauncher.\n\nSource code available at {repoUrl}.'**
  String textAboutDialog(String repoUrl);

  /// No description provided for @textEmptyCategory.
  ///
  /// In en, this message translates to:
  /// **'This category is empty.'**
  String get textEmptyCategory;

  /// No description provided for @time.
  ///
  /// In en, this message translates to:
  /// **'Time'**
  String get time;

  /// No description provided for @titleStatusBarSettingsPage.
  ///
  /// In en, this message translates to:
  /// **'Choose what to display in the status bar'**
  String get titleStatusBarSettingsPage;

  /// No description provided for @tvApplications.
  ///
  /// In en, this message translates to:
  /// **'TV Apps'**
  String get tvApplications;

  /// No description provided for @type.
  ///
  /// In en, this message translates to:
  /// **'Type'**
  String get type;

  /// No description provided for @typeInTheDateFormat.
  ///
  /// In en, this message translates to:
  /// **'Type in the date format'**
  String get typeInTheDateFormat;

  /// No description provided for @typeInTheHourFormat.
  ///
  /// In en, this message translates to:
  /// **'Type in the hour format'**
  String get typeInTheHourFormat;

  /// No description provided for @uninstall.
  ///
  /// In en, this message translates to:
  /// **'Uninstall'**
  String get uninstall;

  /// No description provided for @wallpaper.
  ///
  /// In en, this message translates to:
  /// **'Wallpaper'**
  String get wallpaper;

  /// No description provided for @withEllipsisAddTo.
  ///
  /// In en, this message translates to:
  /// **'Add to...'**
  String get withEllipsisAddTo;

  /// No description provided for @watchNextSectionTitle.
  ///
  /// In en, this message translates to:
  /// **'Watch Next'**
  String get watchNextSectionTitle;

  /// No description provided for @showWatchNextSection.
  ///
  /// In en, this message translates to:
  /// **'Show Watch Next Section'**
  String get showWatchNextSection;

  /// No description provided for @watchNextPermissionTitle.
  ///
  /// In en, this message translates to:
  /// **'Watch Next permission required'**
  String get watchNextPermissionTitle;

  /// No description provided for @watchNextPermissionBody.
  ///
  /// In en, this message translates to:
  /// **'Allow access to TV listings to show your continue watching items.'**
  String get watchNextPermissionBody;

  /// No description provided for @watchNextGrantPermission.
  ///
  /// In en, this message translates to:
  /// **'Grant permission'**
  String get watchNextGrantPermission;

  /// No description provided for @watchNextCheckPermission.
  ///
  /// In en, this message translates to:
  /// **'Check again'**
  String get watchNextCheckPermission;

  /// No description provided for @interface.
  ///
  /// In en, this message translates to:
  /// **'Interface'**
  String get interface;

  /// No description provided for @system.
  ///
  /// In en, this message translates to:
  /// **'System'**
  String get system;

  /// No description provided for @accentColor.
  ///
  /// In en, this message translates to:
  /// **'Accent Color'**
  String get accentColor;

  /// No description provided for @miscellaneous.
  ///
  /// In en, this message translates to:
  /// **'Miscellaneous'**
  String get miscellaneous;

  /// No description provided for @screensaverSettings.
  ///
  /// In en, this message translates to:
  /// **'Screensaver Settings'**
  String get screensaverSettings;

  /// No description provided for @screensaverClockStyle.
  ///
  /// In en, this message translates to:
  /// **'Screensaver Clock Style'**
  String get screensaverClockStyle;

  /// No description provided for @showAppNamesBelowIcons.
  ///
  /// In en, this message translates to:
  /// **'Show App Names Below Icons'**
  String get showAppNamesBelowIcons;

  /// No description provided for @networkIndicator.
  ///
  /// In en, this message translates to:
  /// **'Network Indicator'**
  String get networkIndicator;

  /// No description provided for @customName.
  ///
  /// In en, this message translates to:
  /// **'Custom Name'**
  String get customName;

  /// No description provided for @lastUsed.
  ///
  /// In en, this message translates to:
  /// **'Last Used'**
  String get lastUsed;

  /// No description provided for @watchNextBigPictureMode.
  ///
  /// In en, this message translates to:
  /// **'Immersive mode'**
  String get watchNextBigPictureMode;

  /// No description provided for @watchNextBigPictureModeDescription.
  ///
  /// In en, this message translates to:
  /// **'Show full-width hero backdrop with an action watch button'**
  String get watchNextBigPictureModeDescription;

  /// No description provided for @watchNextWatch.
  ///
  /// In en, this message translates to:
  /// **'Watch'**
  String get watchNextWatch;

  /// No description provided for @watchNextResume.
  ///
  /// In en, this message translates to:
  /// **'Resume'**
  String get watchNextResume;

  /// No description provided for @watchNextRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh content'**
  String get watchNextRefresh;

  /// No description provided for @watchNextRefreshDescription.
  ///
  /// In en, this message translates to:
  /// **'Reload recommendations from media providers'**
  String get watchNextRefreshDescription;

  /// No description provided for @watchNextRefreshed.
  ///
  /// In en, this message translates to:
  /// **'Watch Next content refreshed'**
  String get watchNextRefreshed;

  /// No description provided for @watchNextFilterApps.
  ///
  /// In en, this message translates to:
  /// **'Filter apps'**
  String get watchNextFilterApps;

  /// No description provided for @watchNextFilterAppsDescription.
  ///
  /// In en, this message translates to:
  /// **'Select which apps can display content in Watch Next'**
  String get watchNextFilterAppsDescription;

  /// No description provided for @watchNextNoApps.
  ///
  /// In en, this message translates to:
  /// **'No media apps currently providing Watch Next content'**
  String get watchNextNoApps;

  /// No description provided for @watchNextShowAllApps.
  ///
  /// In en, this message translates to:
  /// **'Show all installed apps'**
  String get watchNextShowAllApps;

  /// No description provided for @watchNextHideWithoutImages.
  ///
  /// In en, this message translates to:
  /// **'Hide programs without image'**
  String get watchNextHideWithoutImages;

  /// No description provided for @watchNextHideWithoutImagesDescription.
  ///
  /// In en, this message translates to:
  /// **'Only show programs that provide poster or backdrop artwork'**
  String get watchNextHideWithoutImagesDescription;

  /// No description provided for @watchNextMaxItems.
  ///
  /// In en, this message translates to:
  /// **'Maximum programs'**
  String get watchNextMaxItems;

  /// No description provided for @watchNextMaxItemsDescription.
  ///
  /// In en, this message translates to:
  /// **'Limit the number of programs displayed in Watch Next'**
  String get watchNextMaxItemsDescription;

  /// No description provided for @watchNextMaxItemsUnlimited.
  ///
  /// In en, this message translates to:
  /// **'Unlimited'**
  String get watchNextMaxItemsUnlimited;

  /// No description provided for @addToCategory.
  ///
  /// In en, this message translates to:
  /// **'Add to row'**
  String get addToCategory;

  /// No description provided for @setCustomBanner.
  ///
  /// In en, this message translates to:
  /// **'Set Custom Banner'**
  String get setCustomBanner;

  /// No description provided for @clearCustomBanner.
  ///
  /// In en, this message translates to:
  /// **'Clear Custom Banner'**
  String get clearCustomBanner;

  /// No description provided for @customBannerSetFailed.
  ///
  /// In en, this message translates to:
  /// **'Failed to set banner: {error}'**
  String customBannerSetFailed(String error);

  /// No description provided for @customBannerClearFailed.
  ///
  /// In en, this message translates to:
  /// **'Failed to clear banner: {error}'**
  String customBannerClearFailed(String error);

  /// No description provided for @preview.
  ///
  /// In en, this message translates to:
  /// **'Preview'**
  String get preview;

  /// No description provided for @colorPurple.
  ///
  /// In en, this message translates to:
  /// **'Purple'**
  String get colorPurple;

  /// No description provided for @colorTeal.
  ///
  /// In en, this message translates to:
  /// **'Teal'**
  String get colorTeal;

  /// No description provided for @colorBlue.
  ///
  /// In en, this message translates to:
  /// **'Blue'**
  String get colorBlue;

  /// No description provided for @colorOrange.
  ///
  /// In en, this message translates to:
  /// **'Orange'**
  String get colorOrange;

  /// No description provided for @colorPink.
  ///
  /// In en, this message translates to:
  /// **'Pink'**
  String get colorPink;

  /// No description provided for @colorGreen.
  ///
  /// In en, this message translates to:
  /// **'Green'**
  String get colorGreen;

  /// No description provided for @colorWhite.
  ///
  /// In en, this message translates to:
  /// **'White'**
  String get colorWhite;

  /// No description provided for @colorYellow.
  ///
  /// In en, this message translates to:
  /// **'Yellow'**
  String get colorYellow;

  /// No description provided for @colorRed.
  ///
  /// In en, this message translates to:
  /// **'Red'**
  String get colorRed;

  /// No description provided for @colorCyan.
  ///
  /// In en, this message translates to:
  /// **'Cyan'**
  String get colorCyan;

  /// No description provided for @colorIndigo.
  ///
  /// In en, this message translates to:
  /// **'Indigo'**
  String get colorIndigo;

  /// No description provided for @colorLime.
  ///
  /// In en, this message translates to:
  /// **'Lime'**
  String get colorLime;

  /// No description provided for @colorAmber.
  ///
  /// In en, this message translates to:
  /// **'Amber'**
  String get colorAmber;

  /// No description provided for @colorRose.
  ///
  /// In en, this message translates to:
  /// **'Rose'**
  String get colorRose;

  /// No description provided for @colorIceBlue.
  ///
  /// In en, this message translates to:
  /// **'Ice Blue'**
  String get colorIceBlue;

  /// No description provided for @clockStyleMinimal.
  ///
  /// In en, this message translates to:
  /// **'Minimal'**
  String get clockStyleMinimal;

  /// No description provided for @clockStyleMinimalDescription.
  ///
  /// In en, this message translates to:
  /// **'Thin, elegant font (Default)'**
  String get clockStyleMinimalDescription;

  /// No description provided for @clockStyleBold.
  ///
  /// In en, this message translates to:
  /// **'Bold'**
  String get clockStyleBold;

  /// No description provided for @clockStyleBoldDescription.
  ///
  /// In en, this message translates to:
  /// **'Thick, highly visible font'**
  String get clockStyleBoldDescription;

  /// No description provided for @clockStyleRetro.
  ///
  /// In en, this message translates to:
  /// **'Retro'**
  String get clockStyleRetro;

  /// No description provided for @clockStyleRetroDescription.
  ///
  /// In en, this message translates to:
  /// **'Monospaced, retro terminal style'**
  String get clockStyleRetroDescription;

  /// No description provided for @clockStyleElegant.
  ///
  /// In en, this message translates to:
  /// **'Elegant'**
  String get clockStyleElegant;

  /// No description provided for @clockStyleElegantDescription.
  ///
  /// In en, this message translates to:
  /// **'Classic serif typeface'**
  String get clockStyleElegantDescription;

  /// No description provided for @clockStyleNeon.
  ///
  /// In en, this message translates to:
  /// **'Neon'**
  String get clockStyleNeon;

  /// No description provided for @clockStyleNeonDescription.
  ///
  /// In en, this message translates to:
  /// **'Ultra-thin, glowing style'**
  String get clockStyleNeonDescription;

  /// No description provided for @clockStylePixel.
  ///
  /// In en, this message translates to:
  /// **'Pixel'**
  String get clockStylePixel;

  /// No description provided for @clockStylePixelDescription.
  ///
  /// In en, this message translates to:
  /// **'Bold monospaced, arcade feel'**
  String get clockStylePixelDescription;

  /// No description provided for @clockStyleDigital.
  ///
  /// In en, this message translates to:
  /// **'Digital'**
  String get clockStyleDigital;

  /// No description provided for @clockStyleDigitalDescription.
  ///
  /// In en, this message translates to:
  /// **'Clean monospaced display'**
  String get clockStyleDigitalDescription;

  /// No description provided for @updateNoReleaseTitle.
  ///
  /// In en, this message translates to:
  /// **'No release available yet'**
  String get updateNoReleaseTitle;

  /// No description provided for @updateNoReleaseBody.
  ///
  /// In en, this message translates to:
  /// **'No stable release with an installable APK has been published yet. Please try again later.'**
  String get updateNoReleaseBody;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'es', 'tr', 'vi'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
    case 'tr':
      return AppLocalizationsTr();
    case 'vi':
      return AppLocalizationsVi();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
