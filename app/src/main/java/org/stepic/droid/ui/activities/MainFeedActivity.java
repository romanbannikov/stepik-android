package org.stepic.droid.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.squareup.otto.Subscribe;
import com.vk.sdk.VKSdk;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.stepic.droid.R;
import org.stepic.droid.analytic.Analytic;
import org.stepic.droid.base.MainApplication;
import org.stepic.droid.core.presenters.ProfileMainFeedPresenter;
import org.stepic.droid.core.presenters.contracts.ProfileMainFeedView;
import org.stepic.droid.events.updating.NeedUpdateEvent;
import org.stepic.droid.model.Profile;
import org.stepic.droid.notifications.StepicInstanceIdService;
import org.stepic.droid.services.UpdateAppService;
import org.stepic.droid.services.UpdateWithApkService;
import org.stepic.droid.ui.dialogs.LoadingProgressDialogFragment;
import org.stepic.droid.ui.dialogs.LogoutAreYouSureDialog;
import org.stepic.droid.ui.dialogs.NeedUpdatingDialog;
import org.stepic.droid.ui.fragments.CertificateFragment;
import org.stepic.droid.ui.fragments.DownloadsFragment;
import org.stepic.droid.ui.fragments.FindCoursesFragment;
import org.stepic.droid.ui.fragments.MyCoursesFragment;
import org.stepic.droid.ui.fragments.NotificationsFragment;
import org.stepic.droid.ui.util.BackButtonHandler;
import org.stepic.droid.ui.util.OnBackClickListener;
import org.stepic.droid.util.AppConstants;
import org.stepic.droid.util.DateTimeHelper;
import org.stepic.droid.util.ProfileExtensionKt;
import org.stepic.droid.util.ProgressHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindDrawable;
import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

public class MainFeedActivity extends BackToExitActivityBase
        implements NavigationView.OnNavigationItemSelectedListener, BackButtonHandler, HasDrawer, ProfileMainFeedView {
    public static final String KEY_CURRENT_INDEX = "Current_index";
    public static final String REMINDER_KEY = "reminder_key";
    private final String PROGRESS_LOGOUT_TAG = "progress_logout";


    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.navigation_view)
    NavigationView navigationView;

    @BindView(R.id.drawer)
    DrawerLayout drawerLayout;

    ImageView profileImage;

    TextView userNameTextView;

    View signInProfileView;

    @BindString(R.string.my_courses_title)
    String coursesTitle;

    @BindDrawable(R.drawable.placeholder_icon)
    Drawable userPlaceholder;

    private int currentIndex;

    GoogleApiClient googleApiClient;

    @Inject
    ProfileMainFeedPresenter profileMainFeedPresenter;

    private List<WeakReference<OnBackClickListener>> onBackClickListenerList = new ArrayList<>(8);
    private ActionBarDrawerToggle actionBarDrawerToggle;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        notificationClickedCheck(intent);
        Bundle extras = intent.getExtras();
        if (extras != null) {
            initFragments(extras);
        }
    }

    private void notificationClickedCheck(Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(AppConstants.OPEN_NOTIFICATION)) {
                analytic.reportEvent(AppConstants.OPEN_NOTIFICATION);
            } else if (action.equals(AppConstants.OPEN_NOTIFICATION_FOR_ENROLL_REMINDER)) {
                String dayTypeString = intent.getStringExtra(REMINDER_KEY);
                if (dayTypeString == null) {
                    dayTypeString = "";
                }
                analytic.reportEvent(Analytic.Notification.REMIND_OPEN, dayTypeString);
                Timber.d(Analytic.Notification.REMIND_OPEN);
                sharedPreferenceHelper.clickEnrollNotification(DateTime.now(DateTimeZone.getDefault()).getMillis());
            } else if (action.equals(AppConstants.OPEN_NOTIFICATION_FROM_STREAK)) {
                sharedPreferenceHelper.resetNumberOfStreakNotifications();
                analytic.reportEvent(Analytic.Streak.STREAK_NOTIFICATION_OPENED);
            } else if (action.equals(AppConstants.OPEN_SHORTCUT_FIND_COURSES)) {
                analytic.reportEvent(Analytic.Shortcut.FIND_COURSES);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                    ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
                    shortcutManager.reportShortcutUsed(AppConstants.FIND_COURSES_SHORTCUT_ID);
                }

            }

            //after tracking check on null user
            if (sharedPreferenceHelper.getAuthResponseFromStore() == null) {
                shell.getScreenProvider().openSplash(this);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainApplication.component().inject(this);
        setContentView(R.layout.activity_main_feed);
        unbinder = ButterKnife.bind(this);
        notificationClickedCheck(getIntent());
        initGoogleApiClient();
        initDrawerHeader();
        setUpToolbar();
        setUpDrawerLayout();
        Bundle extras = getIntent().getExtras();
        if (savedInstanceState != null) {
            initFragments(savedInstanceState);
        } else {
            if (wasLaunchedFromRecents()) {
                initFragments(null);
            } else {
                initFragments(extras);
            }
        }

        bus.register(this);

        profileMainFeedPresenter.attachView(this);
        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //base callback for profile, if it is not loaded.
                analytic.reportEvent(Analytic.Interaction.CLICK_PROFILE_BEFORE_LOADING);
            }
        });
        profileMainFeedPresenter.fetchProfile();

        if (checkPlayServices() && !sharedPreferenceHelper.isGcmTokenOk()) {
            threadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    StepicInstanceIdService.Companion.updateAnywhere(shell.getApi(), sharedPreferenceHelper, analytic); //FCM!
                }
            });
        }

        Intent updateIntent = new Intent(this, UpdateAppService.class);
        startService(updateIntent);
    }

    private void initGoogleApiClient() {
        String serverClientId = config.getGoogleServerClientId();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(Scopes.EMAIL), new Scope(Scopes.PROFILE))
                .requestServerAuthCode(serverClientId)
                .build();

        googleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Toast.makeText(MainApplication.getAppContext(), R.string.connectionProblems, Toast.LENGTH_SHORT).show();
                    }
                } /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    private void initDrawerHeader() {
        View headerLayout = navigationView.getHeaderView(0);
        profileImage = ButterKnife.findById(headerLayout, R.id.profile_image);
        userNameTextView = ButterKnife.findById(headerLayout, R.id.username);
        signInProfileView = ButterKnife.findById(headerLayout, R.id.sign_in_profile_view);

        signInProfileView.setOnClickListener(null);
        signInProfileView.setVisibility(View.INVISIBLE);
        profileImage.setVisibility(View.INVISIBLE);
        userNameTextView.setVisibility(View.INVISIBLE);
        userNameTextView.setText("");
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            if (currentIndex == 0) {
                finish();
                return;
            }
            fragmentBackKeyIntercept();
            FragmentManager fragmentManager = getSupportFragmentManager();
            Fragment fragment = fragmentManager.findFragmentById(R.id.frame);
            fragmentManager.popBackStackImmediate();
            fragmentManager.beginTransaction().remove(fragment).commit();
            if (fragmentManager.getBackStackEntryCount() <= 0) {
                showCurrentFragment(0);

//                super.onBackPressed();
//                finish();
            } else {
                currentIndex = 0;
                navigationView.setCheckedItem(R.id.my_courses);
                setTitle(R.string.my_courses_title);
            }
        }
    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void initFragments(Bundle bundle) {
        if (bundle == null) {
            currentIndex = 0;
        } else {
            currentIndex = bundle.getInt(KEY_CURRENT_INDEX);
        }

        showCurrentFragment(currentIndex);
    }

    private void showCurrentFragment(int currentIndex) {
        this.currentIndex = currentIndex;
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.getItem(currentIndex);
        menuItem.setChecked(true); //when we do not choose in menu
        showCurrentFragment(menuItem);
    }

    private void showCurrentFragment(MenuItem menuItem) {
        setTitle(menuItem.getTitle());
        setFragment(menuItem);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        sendOpenUserAnalytic(menuItem.getItemId());
        switch (menuItem.getItemId()) {
            case R.id.logout_item:
                analytic.reportEvent(Analytic.Interaction.CLICK_LOGOUT);

                LogoutAreYouSureDialog dialog = LogoutAreYouSureDialog.newInstance();
                if (!dialog.isAdded()) {
                    dialog.show(getSupportFragmentManager(), null);
                }

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        drawerLayout.closeDrawers();
                    }
                }, 0);
                return true;
            case R.id.my_settings:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        drawerLayout.closeDrawers();
                    }
                }, 0);
                shell.getScreenProvider().showSettings(this);
                return true;
            case R.id.feedback:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        drawerLayout.closeDrawers();
                    }
                }, 0);
                shell.getScreenProvider().openFeedbackActivity(this);
                return true;
            case R.id.information:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        drawerLayout.closeDrawers();
                    }
                }, 0);
                shell.getScreenProvider().openAboutActivity(this);
            default:
                showCurrentFragment(menuItem);
                break;
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                drawerLayout.closeDrawers();
            }
        }, 0);
        return true;
    }

    private void sendOpenUserAnalytic(int itemId) {
        switch (itemId) {
            case R.id.logout_item:
                analytic.reportEvent(Analytic.Screens.USER_LOGOUT);
                break;
            case R.id.my_settings:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_SETTINGS);
                break;
            case R.id.my_courses:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_MY_COURSES);
                break;
            case R.id.find_lessons:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_FIND_COURSES);
                break;
            case R.id.cached_videos:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_DOWNLOADS);
                break;
            case R.id.feedback:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_FEEDBACK);
                break;
            case R.id.certificates:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_CERTIFICATES);
                break;
            case R.id.notifications:
                analytic.reportEvent(Analytic.Screens.USER_OPEN_NOTIFICATIONS);
                break;
        }
    }

    private void setUpDrawerLayout() {
        navigationView.setNavigationItemSelectedListener(this);
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_closed);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setFragment(MenuItem menuItem) {
        fragmentBackKeyIntercept(); //on back when fragment is changed (work for filter feature)
        Fragment shortLifetimeRef = null;
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.frame);
        String tag = null;
        if (fragment != null) {
            tag = fragment.getTag();
        }
        switch (menuItem.getItemId()) {
            case R.id.my_courses:
                currentIndex = 1;
                if (tag == null || !tag.equals(MyCoursesFragment.class.toString())) {
                    shortLifetimeRef = MyCoursesFragment.newInstance();
                }
                break;
            case R.id.find_lessons:
                currentIndex = 2;
                if (tag == null || !tag.equals(FindCoursesFragment.class.toString())) {
                    shortLifetimeRef = FindCoursesFragment.newInstance();
                }
                break;
            case R.id.cached_videos:
                currentIndex = 3;
                if (tag == null || !tag.equals(DownloadsFragment.class.toString())) {
                    shortLifetimeRef = DownloadsFragment.newInstance();
                }
                break;

            case R.id.certificates:
                currentIndex = 4;
                if (tag == null || !tag.equals(CertificateFragment.class.toString())) {
                    shortLifetimeRef = CertificateFragment.newInstance();
                }
                break;
            case R.id.notifications:
                currentIndex = 5;
                if (tag == null || !tag.equals(NotificationsFragment.class.toString())) {
                    shortLifetimeRef = NotificationsFragment.newInstance();
                }
                break;
        }
        currentIndex--; // menu indices from 1
        if (shortLifetimeRef != null) {

            if (fragment != null) {
                String before = fragment.getTag();
                String now = shortLifetimeRef.getClass().getSimpleName();
                if (!before.equals(now)) {
                    setFragment(R.id.frame, shortLifetimeRef);
                }
            } else {
                setFragment(R.id.frame, shortLifetimeRef);
            }
        }

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(KEY_CURRENT_INDEX, currentIndex);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        profileImage.setOnClickListener(null);
        profileMainFeedPresenter.detachView(this);
        bus.unregister(this);
        drawerLayout.removeDrawerListener(actionBarDrawerToggle);
        super.onDestroy();
    }

    public void showFindLesson() {
        currentIndex = 1;
        showCurrentFragment(currentIndex);
    }

    public static int getFindCoursesIndex() {
        return 1;
    }


    @Subscribe
    public void needUpdateCallback(NeedUpdateEvent event) {

        if (!event.isAppInGp() && event.getLinkForUpdate() == null) {
            return;
        }
        long storedTimestamp = sharedPreferenceHelper.getLastShownUpdatingMessageTimestamp();
        boolean needUpdate = DateTimeHelper.INSTANCE.isNeededUpdate(storedTimestamp, AppConstants.MILLIS_IN_24HOURS);
        if (!needUpdate) return;

        sharedPreferenceHelper.storeLastShownUpdatingMessage();
        analytic.reportEvent(Analytic.Interaction.UPDATING_MESSAGE_IS_SHOWN);
        DialogFragment dialog = NeedUpdatingDialog.Companion.newInstance(event.getLinkForUpdate(), event.isAppInGp());
        dialog.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == AppConstants.REQUEST_EXTERNAL_STORAGE) {
            String permissionExternalStorage = permissions[0];
            if (permissionExternalStorage == null) return;

            if (permissionExternalStorage.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE) &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String link = sharedPreferenceHelper.getTempLink();
                if (link != null) {
                    Intent updateIntent = new Intent(this, UpdateWithApkService.class);
                    updateIntent.putExtra(UpdateWithApkService.Companion.getLinkKey(), link);
                    this.startService(updateIntent);
                }
            }
        }
    }

    public static int getCertificateFragmentIndex() {
        return 3;
    }

    public static int getDownloadFragmentIndex() {
        return 2;
    }

    public static int getMyCoursesIndex() {
        return 0;
    }

    private boolean fragmentBackKeyIntercept() {
        if (onBackClickListenerList != null) {
            for (WeakReference<OnBackClickListener> weakReference : onBackClickListenerList) {
                if (weakReference != null) {
                    OnBackClickListener listener = weakReference.get();
                    if (listener != null) {
                        listener.onBackClick();
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void setBackClickListener(@NotNull OnBackClickListener onBackClickListener) {
        this.onBackClickListenerList.add(new WeakReference<>(onBackClickListener));
    }

    @Override
    public void removeBackClickListener(@NotNull OnBackClickListener onBackClickListener) {
        for (Iterator<WeakReference<OnBackClickListener>> iterator = onBackClickListenerList.iterator();
             iterator.hasNext(); ) {
            WeakReference<OnBackClickListener> weakRef = iterator.next();
            if (weakRef.get() == onBackClickListener) {
                iterator.remove();
            }
        }
    }

    @Override
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    @Override
    public void showAnonymous() {
        signInProfileView.setVisibility(View.VISIBLE);
        profileImage.setVisibility(View.VISIBLE);
        userNameTextView.setVisibility(View.INVISIBLE);
        profileImage.setImageDrawable(userPlaceholder);
        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shell.getScreenProvider().showLaunchScreen(MainFeedActivity.this);
            }
        };
        profileImage.setOnClickListener(onClickListener);
        signInProfileView.setOnClickListener(onClickListener);
        showLogout(false);
    }

    @Override
    public void showProfile(@NotNull Profile profile) {
        signInProfileView.setVisibility(View.INVISIBLE);
        profileImage.setVisibility(View.VISIBLE);
        userNameTextView.setVisibility(View.VISIBLE);
        Glide
                .with(MainFeedActivity.this)
                .load(profile.getAvatar())
                .asBitmap()
                .placeholder(userPlaceholder)
                .into(profileImage);
        userNameTextView.setText(ProfileExtensionKt.getFirstAndLastName(profile));
        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shell.getScreenProvider().openProfile(MainFeedActivity.this);
            }
        });
        showLogout(true);
    }

    void showLogout(boolean needShow) {
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.logout_item);
        menuItem.setVisible(needShow);
    }

    @Override
    public void showLogoutLoading() {
        DialogFragment loadingProgressDialogFragment = LoadingProgressDialogFragment.Companion.newInstance();
        ProgressHelper.activate(loadingProgressDialogFragment, getSupportFragmentManager(), PROGRESS_LOGOUT_TAG);
    }

    @Override
    public void onLogoutSuccess() {
        ProgressHelper.dismiss(getSupportFragmentManager(), PROGRESS_LOGOUT_TAG);
        LoginManager.getInstance().logOut();
        VKSdk.logout();
        if (googleApiClient.isConnected()) {
            Auth.GoogleSignInApi.signOut(googleApiClient);
        }
        shell.getScreenProvider().showLaunchScreen(this);
    }
}