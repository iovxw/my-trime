/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.osfans.trime.ime.core;

import static android.graphics.Color.parseColor;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.blankj.utilcode.util.BarUtils;
import com.osfans.trime.R;
import com.osfans.trime.Rime;
import com.osfans.trime.clipboard.ClipboardDao;
import com.osfans.trime.common.ViewUtils;
import com.osfans.trime.databinding.CompositionRootBinding;
import com.osfans.trime.databinding.InputRootBinding;
import com.osfans.trime.ime.enums.WindowsPositionType;
import com.osfans.trime.ime.keyboard.Event;
import com.osfans.trime.ime.keyboard.InputFeedbackManager;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.ime.keyboard.Keyboard;
import com.osfans.trime.ime.keyboard.KeyboardSwitcher;
import com.osfans.trime.ime.keyboard.KeyboardView;
import com.osfans.trime.ime.lifecycle.LifecycleInputMethodService;
import com.osfans.trime.ime.symbol.LiquidKeyboard;
import com.osfans.trime.ime.symbol.TabManager;
import com.osfans.trime.ime.symbol.TabView;
import com.osfans.trime.ime.text.Candidate;
import com.osfans.trime.ime.text.Composition;
import com.osfans.trime.ime.text.ScrollView;
import com.osfans.trime.ime.text.TextInputManager;
import com.osfans.trime.settings.PrefMainActivity;
import com.osfans.trime.settings.components.ColorPickerDialog;
import com.osfans.trime.settings.components.SchemaPickerDialog;
import com.osfans.trime.settings.components.ThemePickerDialog;
import com.osfans.trime.setup.Config;
import com.osfans.trime.setup.IntentReceiver;
import com.osfans.trime.util.AndroidVersion;
import com.osfans.trime.util.ShortcutUtils;
import com.osfans.trime.util.StringUtils;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlin.jvm.Synchronized;
import timber.log.Timber;

/** {@link InputMethodService ?????????}????????? */
public class Trime extends LifecycleInputMethodService {
  private static Trime self = null;
  private LiquidKeyboard liquidKeyboard;

  @NonNull
  private Preferences getPrefs() {
    return Preferences.Companion.defaultInstance();
  }

  /** ??????????????? */
  @NonNull
  public Config getImeConfig() {
    return Config.get(this);
  }

  private KeyboardView mainKeyboardView; // ????????????
  public KeyboardSwitcher keyboardSwitcher; // ???????????????

  private Candidate mCandidate; // ??????
  private Composition mComposition; // ??????
  private CompositionRootBinding compositionRootBinding = null;
  private ScrollView mCandidateRoot, mTabRoot;
  private TabView tabView;
  public InputRootBinding inputRootBinding = null;
  public CopyOnWriteArrayList<EventListener> eventListeners = new CopyOnWriteArrayList<>();
  public InputMethodManager imeManager = null;
  public InputFeedbackManager inputFeedbackManager = null; // ???????????????
  private IntentReceiver mIntentReceiver = null;

  private boolean isWindowShown = false; // ???????????????????????????

  private boolean isAutoCaps; // ??????????????????
  private final Locale[] locales = new Locale[2];

  private int oneHandMode = 0; // ??????????????????
  public EditorInstance activeEditorInstance;
  public TextInputManager textInputManager; // ?????????????????????

  private final int dialogType =
      VERSION.SDK_INT >= VERSION_CODES.P
          ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
          : WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;

  private boolean isPopupWindowEnabled = true; // ??????????????????
  private String isPopupWindowMovable; // ???????????????????????????
  private int popupWindowX, popupWindowY; // ?????????????????????
  private int popupMargin; // ????????????????????????
  private int popupMarginH; // ?????????????????????????????????
  private boolean isCursorUpdated = false; // ??????????????????
  private int minPopupSize; // ???????????????????????????????????????
  private int minPopupCheckSize; // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????1??????min_length???????????????
  private WindowsPositionType popupWindowPos; // ????????????????????????
  private PopupWindow mPopupWindow;
  private RectF mPopupRectF = new RectF();
  private final Handler mPopupHandler = new Handler(Looper.getMainLooper());
  private final Runnable mPopupTimer =
      new Runnable() {
        @Override
        public void run() {
          if (mCandidateRoot == null || mCandidateRoot.getWindowToken() == null) return;
          if (!isPopupWindowEnabled) return;
          int x, y;
          final int[] mParentLocation = ViewUtils.getLocationOnScreen(mCandidateRoot);
          final int measuredWidth = mCandidateRoot.getWidth() - mPopupWindow.getWidth();
          final int measuredHeight = mPopupWindow.getHeight() + popupMargin;
          if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP || !isCursorUpdated) {
            // setCandidatesViewShown(true);
            switch (popupWindowPos) {
              case TOP_RIGHT:
                x = measuredWidth;
                y = popupMargin;
                break;
              case TOP_LEFT:
                x = 0;
                y = popupMargin;
                break;
              case BOTTOM_RIGHT:
                x = measuredWidth;
                y = mParentLocation[1] - measuredHeight;
                break;
              case DRAG:
                x = popupWindowX;
                y = popupWindowY;
                break;
              case FIXED:
              case BOTTOM_LEFT:
              default:
                x = 0;
                y = mParentLocation[1] - measuredHeight;
                break;
            }
          } else {
            // setCandidatesViewShown(false);
            switch (popupWindowPos) {
              case RIGHT:
              case RIGHT_UP:
                // ????????????bug????????????????????????????????????????????????????????????????????????????????????????????????????????????
                // log??? mCandidateContainer.getWidth()=1328  mFloatingWindow.getWidth()= 1874
                // ??????x??????????????????????????????
                x = Math.max(0, Math.min(measuredWidth, (int) mPopupRectF.right));
                y = Math.max(0, Math.min(measuredHeight, (int) mPopupRectF.top - measuredHeight));
                break;
              case LEFT_UP:
                x = Math.max(0, Math.min(measuredWidth, (int) mPopupRectF.left));
                y = Math.max(0, Math.min(measuredHeight, (int) mPopupRectF.top - measuredHeight));
                break;
              default:
                x = Math.max(0, Math.min(measuredWidth, (int) mPopupRectF.left));
                // popupMargin ?????????????????????????????????
                y = Math.max(0, Math.min(measuredHeight, (int) mPopupRectF.bottom + popupMargin));
                break;
            }
          }
          y -= BarUtils.getStatusBarHeight(); // ??????????????????
          x = Math.max(x, popupMarginH);

          if (!mPopupWindow.isShowing()) {
            mPopupWindow.showAtLocation(mCandidateRoot, Gravity.START | Gravity.TOP, x, y);
          } else {
            mPopupWindow.update(x, y, mPopupWindow.getWidth(), mPopupWindow.getHeight());
          }
        }
      };

  @Synchronized
  @NonNull
  public static Trime getService() {
    assert self != null;
    return self;
  }

  @Synchronized
  @Nullable
  public static Trime getServiceOrNull() {
    return self;
  }

  private static final Handler syncBackgroundHandler =
      new Handler(
          msg -> {
            if (!((Trime) msg.obj).isShowInputRequested()) { // ?????????????????????????????????????????????????????????????????????5??????????????????
              ShortcutUtils.INSTANCE.syncInBackground((Trime) msg.obj);
              ((Trime) msg.obj).loadConfig();
            }
            return false;
          });

  public Trime() {
    try {
      self = this;
      textInputManager = TextInputManager.Companion.getInstance();
    } catch (Exception e) {
      e.fillInStackTrace();
    }
  }

  @Override
  public void onWindowShown() {
    super.onWindowShown();
    if (isWindowShown) {
      Timber.i("Ignoring (is already shown)");
      return;
    } else {
      Timber.i("onWindowShown...");
    }
    isWindowShown = true;

    updateComposing();

    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onWindowShown();
    }
  }

  @Override
  public void onWindowHidden() {
    super.onWindowHidden();
    if (!isWindowShown) {
      Timber.i("Ignoring (is already hidden)");
      return;
    } else {
      Timber.i("onWindowHidden...");
    }
    isWindowShown = false;

    if (getPrefs().getConf().getSyncBackgroundEnabled()) {
      final Message msg = new Message();
      msg.obj = this;
      syncBackgroundHandler.sendMessageDelayed(msg, 5000); // ??????????????????5???????????????????????????
    }

    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onWindowHidden();
    }
  }

  private boolean isWinFixed() {
    return AndroidVersion.INSTANCE.getATMOST_LOLLIPOP()
        || (popupWindowPos != WindowsPositionType.LEFT
            && popupWindowPos != WindowsPositionType.RIGHT
            && popupWindowPos != WindowsPositionType.LEFT_UP
            && popupWindowPos != WindowsPositionType.RIGHT_UP);
  }

  public void updatePopupWindow(final int offsetX, final int offsetY) {
    popupWindowPos = WindowsPositionType.DRAG;
    popupWindowX = offsetX;
    popupWindowY = offsetY;
    Timber.i("updatePopupWindow: winX = %s, winY = %s", popupWindowX, popupWindowY);
    mPopupWindow.update(popupWindowX, popupWindowY, -1, -1, true);
  }

  public void loadConfig() {
    final Config imeConfig = getImeConfig();
    popupWindowPos = imeConfig.getWinPos();
    isPopupWindowMovable = imeConfig.getString("layout/movable");
    popupMargin = imeConfig.getPixel("layout/spacing");
    minPopupSize = imeConfig.getInt("layout/min_length");
    minPopupCheckSize = imeConfig.getInt("layout/min_check");
    popupMarginH = imeConfig.getPixel("layout/real_margin");
    textInputManager.setShouldResetAsciiMode(imeConfig.getBoolean("reset_ascii_mode"));
    isAutoCaps = imeConfig.getBoolean("auto_caps");
    isPopupWindowEnabled =
        getPrefs().getKeyboard().getPopupWindowEnabled() && imeConfig.hasKey("window");
    textInputManager.setShouldUpdateRimeOption(true);
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean updateRimeOption() {
    try {
      if (textInputManager.getShouldUpdateRimeOption()) {
        Rime.setOption("soft_cursor", getPrefs().getKeyboard().getSoftCursorEnabled()); // ?????????
        Rime.setOption("_horizontal", getImeConfig().getBoolean("horizontal")); // ????????????
        textInputManager.setShouldUpdateRimeOption(false);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  @Override
  public void onCreate() {
    // MUST WRAP all code within Service onCreate() in try..catch to prevent any crash loops
    try {
      // Additional try..catch wrapper as the event listeners chain or the super.onCreate() method
      // could crash
      //  and lead to a crash loop
      try {
        Timber.i("onCreate...");

        activeEditorInstance = new EditorInstance(this);
        imeManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputFeedbackManager = new InputFeedbackManager(this);

        keyboardSwitcher = new KeyboardSwitcher();

        liquidKeyboard = new LiquidKeyboard(this, getImeConfig().getClipboardMaxSize());
        clipBoardMonitor();
      } catch (Exception e) {
        super.onCreate();
        e.fillInStackTrace();
        return;
      }
      super.onCreate();
      for (EventListener listener : eventListeners) {
        if (listener != null) listener.onCreate();
      }
    } catch (Exception e) {
      e.fillInStackTrace();
    }
  }

  public void selectLiquidKeyboard(final int tabIndex) {
    final LinearLayout symbolInputView =
        inputRootBinding != null ? inputRootBinding.symbol.symbolInput : null;
    final LinearLayout mainInputView =
        inputRootBinding != null ? inputRootBinding.main.mainInput : null;
    if (symbolInputView != null) {
      if (tabIndex >= 0) {
        final LinearLayout.LayoutParams param =
            (LinearLayout.LayoutParams) symbolInputView.getLayoutParams();
        param.height = mainInputView.getHeight();
        symbolInputView.setVisibility(View.VISIBLE);

        final int orientation = getResources().getConfiguration().orientation;
        liquidKeyboard.setLand(orientation == Configuration.ORIENTATION_LANDSCAPE);
        liquidKeyboard.calcPadding(mainInputView.getWidth());
        liquidKeyboard.select(tabIndex);

        tabView.updateTabWidth();
        if (inputRootBinding != null) {
          mTabRoot.setBackground(mCandidateRoot.getBackground());
          mTabRoot.move(tabView.getHightlightLeft(), tabView.getHightlightRight());
        }
      } else symbolInputView.setVisibility(View.GONE);
    }
    if (mainInputView != null)
      mainInputView.setVisibility(tabIndex >= 0 ? View.GONE : View.VISIBLE);
  }

  // ??????????????????tab name?????????liquidKeyboard?????????tab
  public void selectLiquidKeyboard(@NonNull String name) {
    if (name.matches("\\d+")) selectLiquidKeyboard(Integer.parseInt(name));
    else selectLiquidKeyboard(TabManager.getTagIndex(name));
  }

  public void invalidate() {
    Rime.get(this);
    getImeConfig().destroy();
    reset();
    textInputManager.setShouldUpdateRimeOption(true);
  }

  private void hideCompositionView() {
    if (isPopupWindowMovable.equals("once")) {
      popupWindowPos = getImeConfig().getWinPos();
    }

    if (mPopupWindow != null && mPopupWindow.isShowing()) {
      mPopupWindow.dismiss();
      mPopupHandler.removeCallbacks(mPopupTimer);
    }
  }

  private void showCompositionView() {
    if (TextUtils.isEmpty(Rime.getCompositionText())) {
      hideCompositionView();
      return;
    }
    compositionRootBinding.compositionRoot.measure(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    mPopupWindow.setWidth(compositionRootBinding.compositionRoot.getMeasuredWidth());
    mPopupWindow.setHeight(compositionRootBinding.compositionRoot.getMeasuredHeight());
    mPopupHandler.post(mPopupTimer);
  }

  public void loadBackground() {
    final Config mConfig = getImeConfig();
    final int orientation = getResources().getConfiguration().orientation;

    if (mPopupWindow != null) {
      final Drawable textBackground =
          mConfig.getDrawable(
              "text_back_color",
              "layout/border",
              "border_color",
              "layout/round_corner",
              "layout/alpha");
      if (textBackground != null) mPopupWindow.setBackgroundDrawable(textBackground);
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
        mPopupWindow.setElevation(mConfig.getPixel("layout/elevation"));
    }

    if (mCandidateRoot != null) {
      final Drawable candidateBackground =
          mConfig.getDrawable(
              "candidate_background",
              "candidate_border",
              "candidate_border_color",
              "candidate_border_round",
              null);
      if (candidateBackground != null) mCandidateRoot.setBackground(candidateBackground);
    }

    if (inputRootBinding == null) return;

    int[] padding =
        mConfig.getKeyboardPadding(oneHandMode, orientation == Configuration.ORIENTATION_LANDSCAPE);
    Timber.i(
        "update KeyboardPadding: Trime.loadBackground, padding= %s %s %s",
        padding[0], padding[1], padding[2]);
    mainKeyboardView.setPadding(padding[0], 0, padding[1], padding[2]);

    final Drawable inputRootBackground = mConfig.getDrawable_("root_background");
    if (inputRootBackground != null) {
      inputRootBinding.inputRoot.setBackground(inputRootBackground);
    } else {
      // ????????????????????????????????????????????????
      inputRootBinding.inputRoot.setBackgroundColor(Color.BLACK);
    }

    tabView.reset(self);
  }

  public void resetKeyboard() {
    if (mainKeyboardView != null) {
      mainKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));
      mainKeyboardView.reset(this); // ????????????????????????
    }
  }

  public void resetCandidate() {
    if (mCandidateRoot != null) {
      loadBackground();
      setShowComment(!Rime.getOption("_hide_comment"));
      mCandidateRoot.setVisibility(!Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);
      mCandidate.reset(this);
      isPopupWindowEnabled =
          getPrefs().getKeyboard().getPopupWindowEnabled() && getImeConfig().hasKey("window");
      mComposition.setVisibility(isPopupWindowEnabled ? View.VISIBLE : View.GONE);
      mComposition.reset(this);
    }
  }

  /** ??????????????????????????????????????? !!???????????????????????????Rime.setOption???????????????????????? */
  private void reset() {
    if (inputRootBinding == null) return;
    final LinearLayout symbolInputView = inputRootBinding.symbol.symbolInput;
    final LinearLayout mainInputView = inputRootBinding.main.mainInput;
    if (symbolInputView != null) symbolInputView.setVisibility(View.GONE);
    if (mainInputView != null) mainInputView.setVisibility(View.VISIBLE);
    getImeConfig().reset();
    loadConfig();
    getImeConfig().initCurrentColors();
    if (keyboardSwitcher != null) keyboardSwitcher.newOrReset();
    resetCandidate();
    hideCompositionView();
    resetKeyboard();
  }

  public void initKeyboard() {
    reset();
    setNavBarColor();
    textInputManager.setShouldUpdateRimeOption(true); // ?????????Rime.onMessage?????????set_option????????????
    bindKeyboardToInputView();
    // loadBackground(); // reset()?????????resetCandidate()???resetCandidate()???????????????loadBackground();
    updateComposing(); // ???????????????????????????
  }

  @Override
  public void onDestroy() {
    if (mIntentReceiver != null) mIntentReceiver.unregisterReceiver(this);
    mIntentReceiver = null;
    if (inputFeedbackManager != null) inputFeedbackManager.destroy();
    inputFeedbackManager = null;
    inputRootBinding = null;
    imeManager = null;

    if (getPrefs().getOther().getDestroyOnQuit()) {
      Rime.destroy();
      getImeConfig().destroy();
      System.exit(0); // ????????????
    }
    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onDestroy();
    }
    eventListeners.clear();
    super.onDestroy();

    self = null;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    final Configuration config = getResources().getConfiguration();
    if (config != null) {
      if (config.orientation != newConfig.orientation) {
        // Clear composing text and candidates for orientation change.
        performEscape();
        config.orientation = newConfig.orientation;
      }
    }
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      final int i = cursorAnchorInfo.getComposingTextStart();
      switch (popupWindowPos) {
        case LEFT:
        case LEFT_UP:
          if (i >= 0) {
            mPopupRectF = cursorAnchorInfo.getCharacterBounds(i);
          }
          break;
        default:
          mPopupRectF.left = cursorAnchorInfo.getInsertionMarkerHorizontal();
          mPopupRectF.top = cursorAnchorInfo.getInsertionMarkerTop();
          mPopupRectF.right = mPopupRectF.left;
          mPopupRectF.bottom = cursorAnchorInfo.getInsertionMarkerBottom();
          break;
      }
      cursorAnchorInfo.getMatrix().mapRect(mPopupRectF);
      if (mCandidateRoot != null) {
        showCompositionView();
      }
    }
  }

  @Override
  public void onUpdateSelection(
      int oldSelStart,
      int oldSelEnd,
      int newSelStart,
      int newSelEnd,
      int candidatesStart,
      int candidatesEnd) {
    super.onUpdateSelection(
        oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    if ((candidatesEnd != -1) && ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
      // ?????????????????????????????????
      if ((newSelEnd < candidatesEnd) && (newSelEnd >= candidatesStart)) {
        final int n = newSelEnd - candidatesStart;
        Rime.RimeSetCaretPos(n);
        updateComposing();
      }
    }
    if ((candidatesStart == -1 && candidatesEnd == -1) && (newSelStart == 0 && newSelEnd == 0)) {
      // ???????????????????????????
      performEscape();
    }
    // Update the caps-lock status for the current cursor position.
    dispatchCapsStateToInputView();
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    outInsets.contentTopInsets = outInsets.visibleTopInsets;
  }

  @Override
  public View onCreateInputView() {
    // ?????????????????????
    super.onCreateInputView();
    inputRootBinding = InputRootBinding.inflate(LayoutInflater.from(this));
    mainKeyboardView = inputRootBinding.main.mainKeyboardView;

    // ??????????????????
    mCandidateRoot = inputRootBinding.main.candidateView.candidateRoot;
    mCandidate = inputRootBinding.main.candidateView.candidates;

    // ???????????????????????????
    compositionRootBinding = CompositionRootBinding.inflate(LayoutInflater.from(this));
    mComposition = compositionRootBinding.compositions;
    mPopupWindow = new PopupWindow(compositionRootBinding.compositionRoot);
    mPopupWindow.setClippingEnabled(false);
    mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    if (AndroidVersion.INSTANCE.getATLEAST_M()) {
      mPopupWindow.setWindowLayoutType(dialogType);
    }
    hideCompositionView();
    mTabRoot = inputRootBinding.symbol.tabView.tabRoot;

    liquidKeyboard.setView(inputRootBinding.symbol.liquidKeyboardView);
    tabView = inputRootBinding.symbol.tabView.tabs;

    for (EventListener listener : eventListeners) {
      assert inputRootBinding != null;
      if (listener != null) listener.onInitializeInputUi(inputRootBinding);
    }
    getImeConfig().initCurrentColors();
    loadBackground();

    return inputRootBinding.inputRoot;
  }

  public void setShowComment(boolean show_comment) {
    // if (mCandidateRoot != null) mCandidate.setShowComment(show_comment);
    mComposition.setShowComment(show_comment);
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onStartInputView(activeEditorInstance, restarting);
    }
    if (getPrefs().getOther().getShowStatusBarIcon()) {
      showStatusIcon(R.drawable.ic_status); // ???????????????
    }
    bindKeyboardToInputView();
    if (!restarting) setNavBarColor();
    setCandidatesViewShown(!Rime.isEmpty()); // ?????????????????????????????????
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    super.onFinishInputView(finishingInput);
    // Dismiss any pop-ups when the input-view is being finished and hidden.
    mainKeyboardView.closing();
    performEscape();
    try {
      hideCompositionView();
    } catch (Exception e) {
      Timber.e(e, "Failed to show the PopupWindow.");
    }
  }

  public void bindKeyboardToInputView() {
    if (mainKeyboardView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = keyboardSwitcher.getCurrentKeyboard();
      mainKeyboardView.setKeyboard(sk);
      dispatchCapsStateToInputView();
    }
  }

  /**
   * Dispatches cursor caps info to input view in order to implement auto caps lock at the start of
   * a sentence.
   */
  private void dispatchCapsStateToInputView() {
    if ((isAutoCaps || Rime.isAsciiMode())
        && (mainKeyboardView != null && !mainKeyboardView.isCapsOn())) {
      mainKeyboardView.setShifted(false, activeEditorInstance.getCursorCapsMode() != 0);
    }
  }

  private boolean isComposing() {
    return Rime.isComposing();
  }

  public void commitText(String text) {
    activeEditorInstance.commitText(text, true);
  }

  /**
   * ?????????{@link KeyEvent#KEYCODE_BACK Back???}??????????????????
   *
   * @param keyCode {@link KeyEvent#getKeyCode() ??????}
   * @return ???????????????Back?????????
   */
  private boolean handleBack(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
      requestHideSelf(0);
      return true;
    }
    return false;
  }

  public boolean onRimeKey(int[] event) {
    updateRimeOption();
    final boolean ret = Rime.onKey(event);
    activeEditorInstance.commitRimeText();
    return ret;
  }

  private boolean composeEvent(@NonNull KeyEvent event) {
    final int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_MENU) return false; // ????????? Menu ???
    if (keyCode >= Key.getSymbolStart()) return false; // ???????????????????????????
    if (event.getRepeatCount() == 0 && KeyEvent.isModifierKey(keyCode)) {
      boolean ret =
          onRimeKey(
              Event.getRimeEvent(
                  keyCode, event.getAction() == KeyEvent.ACTION_DOWN ? 0 : Rime.META_RELEASE_ON));
      if (isComposing()) setCandidatesViewShown(textInputManager.isComposable()); // ????????????????????????????????????
      return ret;
    }
    return textInputManager.isComposable() && !Rime.isVoidKeycode(keyCode);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Timber.i("onKeyDown = %s", event);
    if (composeEvent(event) && onKeyEvent(event)) return true;
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Timber.i("onKeyUp = %s", event);
    if (composeEvent(event) && textInputManager.getNeedSendUpRimeKey()) {
      textInputManager.onRelease(keyCode);
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  /**
   * ????????????????????????
   *
   * @param event {@link KeyEvent ????????????}
   * @return ??????????????????
   */
  private boolean onKeyEvent(@NonNull KeyEvent event) {
    Timber.i("onKeyEvent = %s", event);
    int keyCode = event.getKeyCode();
    textInputManager.setNeedSendUpRimeKey(Rime.isComposing());
    if (!isComposing()) {
      if (keyCode == KeyEvent.KEYCODE_DEL
          || keyCode == KeyEvent.KEYCODE_ENTER
          || keyCode == KeyEvent.KEYCODE_ESCAPE
          || keyCode == KeyEvent.KEYCODE_BACK) {
        return false;
      }
    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
      keyCode = KeyEvent.KEYCODE_ESCAPE; // ???????????????
    }

    if (event.getAction() == KeyEvent.ACTION_DOWN
        && event.isCtrlPressed()
        && event.getRepeatCount() == 0
        && !KeyEvent.isModifierKey(keyCode)) {
      try {
        textInputManager.handleEditorAction(keyCode, event.getMetaState());
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    final int unicodeChar = event.getUnicodeChar();
    final String s = String.valueOf((char) unicodeChar);
    final int i = Event.getClickCode(s);
    int mask = 0;
    if (i > 0) {
      keyCode = i;
    } else { // ??????????????????
      mask = event.getMetaState();
    }
    final boolean ret = handleKey(keyCode, mask);
    if (isComposing()) setCandidatesViewShown(textInputManager.isComposable()); // ????????????????????????????????????
    return ret;
  }

  public void switchToPrevIme() {
    try {
      if (VERSION.SDK_INT >= VERSION_CODES.P) {
        switchToPreviousInputMethod();
      } else {
        Window window = getWindow().getWindow();
        if (window != null) {
          if (imeManager != null) {
            imeManager.switchToLastInputMethod(window.getAttributes().token);
          }
        }
      }
    } catch (Exception e) {
      Timber.e(e, "Unable to switch to the previous IME.");
      if (imeManager != null) {
        imeManager.showInputMethodPicker();
      }
    }
  }

  public void switchToNextIme() {
    try {
      if (VERSION.SDK_INT >= VERSION_CODES.P) {
        switchToNextInputMethod(false);
      } else {
        Window window = getWindow().getWindow();
        if (window != null) {
          if (imeManager != null) {
            imeManager.switchToNextInputMethod(window.getAttributes().token, false);
          }
        }
      }
    } catch (Exception e) {
      Timber.e(e, "Unable to switch to the next IME.");
      if (imeManager != null) {
        imeManager.showInputMethodPicker();
      }
    }
  }

  public boolean handleKey(int keyEventCode, int metaState) { // ?????????
    textInputManager.setNeedSendUpRimeKey(false);
    if (onRimeKey(Event.getRimeEvent(keyEventCode, metaState))) {
      textInputManager.setNeedSendUpRimeKey(true);
      Timber.i("Rime onKey");
    } else if (performEnter(keyEventCode) || handleBack(keyEventCode)) {
      Timber.i("Trime onKey");
    } else if (ShortcutUtils.INSTANCE.openCategory(keyEventCode)) {
      Timber.i("Open category");
    } else {
      textInputManager.handleMenu(keyEventCode);
      textInputManager.handleEditorAction(keyEventCode, metaState);
      textInputManager.setNeedSendUpRimeKey(true);
      return false;
    }
    return true;
  }

  /** ?????????????????????????????????????????????????????????/????????????/??????????????????????????????????????? */
  /*
  private String getActiveText(int type) {
    if (type == 2) return Rime.RimeGetInput(); // ????????????
    String s = Rime.getComposingText(); // ????????????
    if (TextUtils.isEmpty(s)) {
      final InputConnection ic = getCurrentInputConnection();
      CharSequence cs = ic != null ? ic.getSelectedText(0) : null; // ?????????
      if (type == 1 && TextUtils.isEmpty(cs)) cs = lastCommittedText; // ????????????
      if (TextUtils.isEmpty(cs) && ic != null) {
        cs = ic.getTextBeforeCursor(type == 4 ? 1024 : 1, 0); // ????????????
      }
      if (TextUtils.isEmpty(cs) && ic != null) cs = ic.getTextAfterCursor(1024, 0); // ?????????????????????
      if (cs != null) s = cs.toString();
    }
    return s;
  } */

  /** ??????Rime???????????????????????????????????? */
  public void updateComposing() {
    final @Nullable InputConnection ic = getCurrentInputConnection();
    activeEditorInstance.updateComposingText();
    if (ic != null && !isWinFixed()) isCursorUpdated = ic.requestCursorUpdates(1);
    if (mCandidateRoot != null) {
      if (isPopupWindowEnabled) {
        final int startNum = mComposition.setWindow(minPopupSize, minPopupCheckSize);
        mCandidate.setText(startNum);
        if (!isCursorUpdated) showCompositionView();
      } else {
        mCandidate.setText(0);
      }
      // ????????????????????????????????????????????????????????????????????????
      mTabRoot.move(mCandidate.getHighlightLeft(), mCandidate.getHighlightRight());
    }
    if (mainKeyboardView != null) mainKeyboardView.invalidateComposingKeys();
    if (!onEvaluateInputViewShown())
      setCandidatesViewShown(textInputManager.isComposable()); // ????????????????????????????????????
  }

  private void showDialog(@NonNull Dialog dialog) {
    final Window window = dialog.getWindow();
    final WindowManager.LayoutParams lp = window.getAttributes();
    lp.token = getWindow().getWindow().getDecorView().getWindowToken();
    lp.type = dialogType;
    window.setAttributes(lp);
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  /** ??????{@link ColorPickerDialog ???????????????} */
  public void showColorDialog() {
    new ColorPickerDialog(this).show();
  }

  /** ??????{@link SchemaPickerDialog ????????????????????????} */
  public void showSchemaDialog() {
    new SchemaPickerDialog(this).show();
  }

  /** ??????{@link ThemePickerDialog ???????????????} */
  public void showThemeDialog() {
    new ThemePickerDialog(this).show();
  }

  /** Hides the IME and launches {@link PrefMainActivity}. */
  public void launchSettings() {
    requestHideSelf(0);
    final Intent i = new Intent(this, PrefMainActivity.class);
    i.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    getApplicationContext().startActivity(i);
  }

  public void showOptionsDialog() {
    final AlertDialog.Builder dialogBuilder =
        new AlertDialog.Builder(this, R.style.AlertDialogTheme);
    dialogBuilder
        .setTitle(R.string.trime_app_name)
        .setIcon(R.mipmap.ic_app_icon)
        .setCancelable(true)
        .setNegativeButton(
            R.string.other_ime,
            (dialog, which) -> {
              dialog.dismiss();
              if (imeManager != null) {
                imeManager.showInputMethodPicker();
              }
            })
        .setPositiveButton(
            R.string.set_ime,
            (dialog, which) -> {
              launchSettings();
              dialog.dismiss();
            });
    if (Rime.get_current_schema().contentEquals(".default")) {
      dialogBuilder.setMessage(R.string.no_schemas);
    } else {
      dialogBuilder
          .setNegativeButton(
              R.string.pref_schemas,
              (dialog, which) -> {
                showSchemaDialog();
                dialog.dismiss();
              })
          .setSingleChoiceItems(
              Rime.getSchemaNames(),
              Rime.getSchemaIndex(),
              (dialog, id) -> {
                dialog.dismiss();
                Rime.selectSchema(id);
                textInputManager.setShouldUpdateRimeOption(true);
              });
    }
    showDialog(dialogBuilder.create());
  }

  /**
   * ?????????{@link KeyEvent#KEYCODE_ENTER ?????????}????????????
   *
   * @param keyCode {@link KeyEvent#getKeyCode() ??????}
   * @return ???????????????????????????
   */
  private boolean performEnter(int keyCode) { // ??????
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      if (textInputManager.getPerformEnterAsLineBreak()) {
        commitText("\n");
      } else {
        sendKeyChar('\n');
      }
      return true;
    }
    return false;
  }

  /** ??????PC?????????Esc???????????????????????????????????????????????? */
  private void performEscape() {
    if (isComposing()) textInputManager.onKey(KeyEvent.KEYCODE_ESCAPE, 0);
  }

  private void setNavBarColor() {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      try {
        final Window window = getWindow().getWindow();
        @ColorInt final Integer keyboardBackColor = getImeConfig().getCurrentColor_("back_color");
        if (keyboardBackColor != null) {
          BarUtils.setNavBarColor(window, keyboardBackColor);
        }
      } catch (Exception e) {
        Timber.e(e);
      }
    }
  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    final Configuration config = getResources().getConfiguration();
    if (config != null) {
      if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
        return false;
      } else {
        switch (getPrefs().getKeyboard().getFullscreenMode()) {
          case AUTO_SHOW:
            final EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
              return false;
            }
          case ALWAYS_SHOW:
            return true;
          case NEVER_SHOW:
            return false;
        }
      }
    }
    return false;
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParameters();
  }

  /** Updates the layout params of the window and input view. */
  private void updateSoftInputWindowLayoutParameters() {
    final Window w = getWindow().getWindow();
    if (w == null) return;
    final LinearLayout inputRoot = inputRootBinding != null ? inputRootBinding.inputRoot : null;
    if (inputRoot != null) {
      final int layoutHeight =
          isFullscreenMode()
              ? WindowManager.LayoutParams.WRAP_CONTENT
              : WindowManager.LayoutParams.MATCH_PARENT;
      final View inputArea = w.findViewById(android.R.id.inputArea);
      // TODO: ???????????????????????????????????????????????????????????????????????????
      if (isFullscreenMode()) {
        Timber.i("isFullscreenMode");
        /* In Fullscreen mode, when layout contains transparent color,
         * the background under input area will disturb users' typing,
         * so set the input area as light pink */
        inputArea.setBackgroundColor(parseColor("#ff660000"));
      } else {
        Timber.i("NotFullscreenMode");
        /* Otherwise, set it as light gray to avoid potential issue */
        inputArea.setBackgroundColor(parseColor("#dddddddd"));
      }

      ViewUtils.updateLayoutHeightOf(inputArea, layoutHeight);
      ViewUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM);
      ViewUtils.updateLayoutHeightOf(inputRoot, layoutHeight);
    }
  }

  public boolean addEventListener(@NonNull EventListener listener) {
    return eventListeners.add(listener);
  }

  public boolean removeEventListener(@NonNull EventListener listener) {
    return eventListeners.remove(listener);
  }

  public interface EventListener {
    default void onCreate() {}

    default void onInitializeInputUi(@NonNull InputRootBinding uiBinding) {}

    default void onDestroy() {}

    default void onStartInputView(@NonNull EditorInstance instance, boolean restarting) {}

    default void osFinishInputView(boolean finishingInput) {}

    default void onWindowShown() {}

    default void onWindowHidden() {}

    default void onUpdateSelection() {}
  }

  private String ClipBoardString = "";

  /**
   * ??????????????????????????????????????????????????????????????????????????????????????????????????????
   *
   * <p>ClipBoardCompare ???????????????????????????????????????????????????????????? ClipBoardCompare ???????????? string?????????????????????????????????????????????????????????
   * ClipBoardOut ???????????????????????????????????????????????????????????????????????????????????????
   */
  private void clipBoardMonitor() {
    ClipboardDao.get();
    final ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    final Config imeConfig = getImeConfig();
    clipBoard.addPrimaryClipChangedListener(
        () -> {
          if (imeConfig.getClipboardMaxSize() != 0) {
            final ClipData clipData = clipBoard.getPrimaryClip();
            final ClipData.Item item = clipData.getItemAt(0);
            if (item == null) return;

            final String rawText = item.coerceToText(self).toString();
            final String filteredText =
                StringUtils.replace(rawText, imeConfig.getClipBoardCompare());
            if (filteredText.length() < 1 || filteredText.equals(ClipBoardString)) return;

            if (StringUtils.mismatch(rawText, imeConfig.getClipBoardOutput())) {
              ClipBoardString = filteredText;
              liquidKeyboard.addClipboardData(rawText);
            }
          }
        });
  }
}
