package com.omnia.backend.service.impl;
import com.google.firebase.*;
import com.google.firebase.messaging.*;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class FirebasePushSender {
 private final FirebaseMessaging messaging;
 public FirebasePushSender(@Value("${omnia.push.enabled:false}")boolean enabled,@Value("${omnia.push.project-id:omnia-996c0}")String project)throws java.io.IOException{
  if(enabled){FirebaseOptions options=FirebaseOptions.builder().setProjectId(project).setCredentials(GoogleCredentials.getApplicationDefault()).build();FirebaseApp app=FirebaseApp.initializeApp(options,"omnia-push");messaging=FirebaseMessaging.getInstance(app);}else messaging=null;
 }
 public boolean ready(){return messaging!=null;}
 public void send(String token,Map<String,String> data)throws FirebaseMessagingException{
  if(messaging==null)throw new IllegalStateException("Push not configured");messaging.send(Message.builder().setToken(token).putAllData(data).setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).setTtl(90000).setRestrictedPackageName("com.omnia.shop.debug").build()).build());
 }
}
