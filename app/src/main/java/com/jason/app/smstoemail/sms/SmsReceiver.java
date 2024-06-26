package com.jason.app.smstoemail.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.jason.app.smstoemail.MainSmsActivity;
import com.jason.app.smstoemail.EmailMager;
import com.jason.app.smstoemail.R;
import com.jason.app.smstoemail.fragments.SettingsFragment;
import com.jason.app.smstoemail.utils.AndrUtils;
import com.jason.app.smstoemail.views.SmsMsg;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.nio.charset.StandardCharsets;


public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault());

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "onReceive");
        SettingsFragment.load(context);
        SmsLocalManager.getInstace().init(context);
        SmsLocalManager.getInstace().load();
        smsReceived(context, intent);
    }

    private void smsReceived(Context context, Intent intent) {
        //有短信到达
        SmsManager sms = SmsManager.getDefault();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            // String slot = String.valueOf(intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1));
            SmsMessage[] messages = new SmsMessage[pdus.length];
            //解析
            for (int i = 0; i < pdus.length; i++)
                messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            //按照时间排序
            Arrays.sort(messages, new Comparator<SmsMessage>() {

                @Override
                public int compare(SmsMessage o1, SmsMessage o2) {
                    if (o1.getTimestampMillis() == o2.getTimestampMillis()) {
                        return 0;
                    } else if (o1.getTimestampMillis() > o2.getTimestampMillis()) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });
            //分类合并,排版
            StringBuilder contbuf = null;
            String keyword = "NULL";
            for (SmsMessage msg : messages) {
                String content = msg.getMessageBody();
                String from = msg.getOriginatingAddress();
                String servicecenter = msg.getServiceCenterAddress();
                // String userdata = String.valueOf(msg.getSubscriptionId()); 
                // String indexonicc = String.valueOf(msg.getIndexOnIcc());
                // String protocolidentifier = String.valueOf(msg.getProtocolIdentifier());
                // StringBuilder sb = new StringBuilder();
                // for (byte b : msg.getPdu()) {
                //     sb.append(String.format("%02X", b));
                // }
                // String pdu = sb.toString();
                long time = msg.getTimestampMillis();
                Pattern pattern = Pattern.compile("【(.*?)】");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    keyword = matcher.group(1);
                } else {
                    if (content.length() <= 10) {
                        keyword =  content;
                    } else {
                        keyword =  content.substring(0, 10);
                    }
                }
                
                if (!SmsLocalManager.getInstace().isMerger()) {
                    //逐条显示
                    sendToView(context, new SmsMsg(msg));
                }
                if (contbuf == null) {
                    contbuf = new StringBuilder();
                    StringBuilder sbf = new StringBuilder();
                    sbf.append(content).append("\n");
                    sbf.append("\n\n====== ").append(sdf.format(new Date(time))).append(" ======\n");
                    // sbf.append("---------------------------\n");
                    sbf.append("Pass\t").append(servicecenter).append("\n");
                    sbf.append("From\t").append(from).append("\n");
                    // sbf.append("Slot Index: ").append(slot).append("\n");
                    // sbf.append("Index On ICC: ").append(indexonicc).append("\n");
                    // sbf.append("Protocol Identifier: ").append(protocolidentifier).append("\n");
                    // sbf.append("PDU: ").append(pdu).append("\n");
                    // sbf.append(sdf.format(new Date(time))).append("\n");
//                    sbf.append(context.getString(R.string.content)).append("\n");
//                    sbf.append("------------").append(sdf.format(new Date(time))).append("------------\n");

                    contbuf.append(sbf.toString()).append("\n");
                } else {
//                    if (!SmsLocalManager.getInstace().isMerger()) {
//                        contbuf.append("------------").append(sdf.format(new Date(time))).append("------------\n");
//                    }
                    contbuf.append(content);
                }
            }
            //组装字符串发送
            Log.e(TAG, "v=\n" + contbuf.toString());
            boolean isSuc = EmailMager.getInstance().sendMail(keyword, contbuf.toString());
            if (isSuc) {
                Log.i(TAG, "sended");
            } else {
                sendTips(context.getString(R.string.sendedFailed));
                Log.i(TAG, context.getString(R.string.sendedFailed));
            }
            //send to view
            if (SmsLocalManager.getInstace().isMerger() && messages.length > 0) {
                //合并显示
                SmsMessage msg = messages[0];
                if (msg != null) {
                    String from = msg.getDisplayOriginatingAddress();
                    long time = msg.getTimestampMillis();
                    sendToView(context, new SmsMsg(time, from, contbuf.toString()));
                }
            }
        }
    }

    //通知界面显示
    private void sendToView(Context con, SmsMsg smsMsg) {
//        SmsMsg smsMsg = new SmsMsg(s);
        SmsLocalManager.getInstace().add(smsMsg);
        if (MainSmsActivity.Inst() != null && !AndrUtils.isBackground(con)) {
            MainSmsActivity.Inst().addSms(smsMsg);
        }
    }

    private void sendTips(String s) {
        if (MainSmsActivity.Inst() != null) {
            MainSmsActivity.Inst().sendTips(s);
        }
    }
}
