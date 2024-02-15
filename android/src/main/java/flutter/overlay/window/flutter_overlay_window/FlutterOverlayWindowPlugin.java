package flutter.overlay.window.flutter_overlay_window;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.FlutterEngineGroup;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.JSONMessageCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterOverlayWindowPlugin implements
        FlutterPlugin, ActivityAware, BasicMessageChannel.MessageHandler, MethodCallHandler,
        PluginRegistry.ActivityResultListener {

    private MethodChannel channel;
    private Context context;
    private Activity mActivity;
    private BasicMessageChannel<Object> messenger;
    private Result pendingResult;
    final int REQUEST_CODE_FOR_OVERLAY_PERMISSION = 1248;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        try {
            if (this.context == null) {
                this.context = flutterPluginBinding.getApplicationContext();

                channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), OverlayConstants.CHANNEL_TAG);
                channel.setMethodCallHandler(this);

                messenger = new BasicMessageChannel(flutterPluginBinding.getBinaryMessenger(), OverlayConstants.MESSENGER_TAG,
                        JSONMessageCodec.INSTANCE);
                messenger.setMessageHandler(this);

                WindowSetup.messenger = messenger;
                WindowSetup.messenger.setMessageHandler(this);
                Log.d("OverLay", "onAttachedToEngine started");
                Log.d("OverLay", "onAttachedToEngine messenger:" + messenger.toString());

            }
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTraceString = sw.toString();
            Log.d("OverLay", "onAttachedToEngine GetMessage:" + ex.getMessage());
            Log.d("OverLay", "onAttachedToEngine stackTraceString:" + stackTraceString);


        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            pendingResult = result;
            Log.d("OverLay", "onMethodCall, pendingResult:" + pendingResult);
            Log.d("OverLay", "onMethodCall, call.method:" + call.method);

            if (call.method.equals("checkPermission")) {
                result.success(checkOverlayPermission());
            } else if (call.method.equals("requestPermission")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(Uri.parse("package:" + mActivity.getPackageName()));
                    mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_OVERLAY_PERMISSION);
                } else {
                    result.success(true);
                }
            } else if (call.method.equals("showOverlay")) {

                if (!checkOverlayPermission()) {
                    result.error("PERMISSION", "overlay permission is not enabled", null);
                    return;
                }
                Log.d("OverLay", "FlutterOverlayWindowPlugin showOverlay:" + call.arguments);

                Integer height = call.argument("height");
                Integer width = call.argument("width");
                String alignment = call.argument("alignment");
                String flag = call.argument("flag");
                String overlayTitle = call.argument("overlayTitle");
                String overlayContent = call.argument("overlayContent");
                String notificationVisibility = call.argument("notificationVisibility");
                boolean enableDrag = call.argument("enableDrag");
                String positionGravity = call.argument("positionGravity");

                WindowSetup.width = width != null ? width : -1;
                WindowSetup.height = height != null ? height : -1;
                WindowSetup.enableDrag = enableDrag;
                WindowSetup.setGravityFromAlignment(alignment != null ? alignment : "center");
                WindowSetup.setFlag(flag != null ? flag : "flagNotFocusable");
                WindowSetup.overlayTitle = overlayTitle;
                WindowSetup.overlayContent = overlayContent == null ? "" : overlayContent;
                WindowSetup.positionGravity = positionGravity;
                WindowSetup.setNotificationVisibility(notificationVisibility);

                final Intent intent = new Intent(context, OverlayService.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startService(intent);
                result.success(null);

            } else if (call.method.equals("isOverlayActive")) {
                result.success(OverlayService.isRunning);
                return;
            } else if (call.method.equals("closeOverlay")) {
                if (OverlayService.isRunning) {
                    final Intent i = new Intent(context, OverlayService.class);
                    i.putExtra(OverlayService.INTENT_EXTRA_IS_CLOSE_WINDOW, true);
                    context.startService(i);
                    result.success(true);
                }
                return;
            } else {
                result.notImplemented();
            }
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTraceString = sw.toString();
            Log.d("OverLay", "onMethodCall GetMessage:" + ex.getMessage());
            Log.d("OverLay", "onMethodCall stackTraceString:" + stackTraceString);


        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        try {
            this.context = null;
            channel.setMethodCallHandler(null);
            WindowSetup.messenger.setMessageHandler(null);
            WindowSetup.messenger = null;
            channel = null;
            Log.d("OverLay", "onDetachedFromEngine run()");
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTraceString = sw.toString();
            Log.d("OverLay", "onDetachedFromEngine GetMessage:" + ex.getMessage());
            Log.d("OverLay", "onDetachedFromEngine stackTraceString:" + stackTraceString);


        }


    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        try {
            Log.d("OverLay", "onAttachedToActivity run:binding.getActivity()" + binding.getActivity());

            mActivity = binding.getActivity();
            FlutterEngineGroup enn = new FlutterEngineGroup(context);
            DartExecutor.DartEntrypoint dEntry = new DartExecutor.DartEntrypoint(
                    FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                    "overlayMain");
            FlutterEngine engine = enn.createAndRunEngine(context, dEntry);
            FlutterEngineCache.getInstance().put(OverlayConstants.CACHED_TAG, engine);
            binding.addActivityResultListener(this);

        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTraceString = sw.toString();
            Log.d("OverLay", "onAttachedToActivity GetMessage:" + ex.getMessage());
            Log.d("OverLay", "onAttachedToActivity stackTraceString:" + stackTraceString);
        }
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        Log.d("OverLay", "onDetachedFromActivityForConfigChanges run()");
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        try {
            Log.d("OverLay", "onReattachedToActivityForConfigChanges run:binding.getActivity()" + binding.getActivity());
            this.mActivity = binding.getActivity();
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTraceString = sw.toString();
            Log.d("OverLay", "onReattachedToActivityForConfigChanges GetMessage:" + ex.getMessage());
            Log.d("OverLay", "onReattachedToActivityForConfigChanges stackTraceString:" + stackTraceString);
        }
    }

    @Override
    public void onDetachedFromActivity() {
        Log.d("OverLay", "onDetachedFromActivity run()");
    }

    @Override
    public void onMessage(@Nullable Object message, @NonNull BasicMessageChannel.Reply reply) {
        try {
            BasicMessageChannel overlayMessageChannel = new BasicMessageChannel(
                    FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG)
                            .getDartExecutor(),
                    OverlayConstants.MESSENGER_TAG, JSONMessageCodec.INSTANCE);
            overlayMessageChannel.send(message, reply);
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String stackTraceString = sw.toString();
            Log.d("OverLay", "onMessage GetMessage:" + ex.getMessage());
            Log.d("OverLay", "onMessage stackTraceString:" + stackTraceString);
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_FOR_OVERLAY_PERMISSION) {
            pendingResult.success(checkOverlayPermission());
            return true;
        }
        return false;
    }

}
