package br.com.calculacorrida

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PxRadioTest {
    @Test fun optInBubbleHoldReleaseCancelAndStopAreIndependentOfReader()=fixture{f->
        assertFalse(PxService.status.value.active);assertFalse(PxService.overlayVisibleForTest());assertEquals(0,f.enabled.get())
        f.join();f.waitFor("PX connects"){PxService.status.value.connected};assertFalse("No duplicate overlay inside the PX screen",PxService.overlayVisibleForTest());f.screenshot()
        assertFalse(PxService.status.value.talking);assertEquals(0,f.enabled.get());assertEquals(f.readerSetting,f.context.getSharedPreferences("settings",0).getBoolean("reader_paused",false))
        f.touch(MotionEvent.ACTION_DOWN);f.waitFor("press publishes"){f.enabled.get()==1&&PxService.status.value.talking};f.touch(MotionEvent.ACTION_UP);f.waitFor("release mutes"){!f.microphone};assertFalse(PxService.status.value.talking)
        f.touch(MotionEvent.ACTION_DOWN);f.waitFor("second capture starts"){f.enabled.get()==2};f.touch(MotionEvent.ACTION_CANCEL);f.waitFor("cancel mutes"){!f.microphone}
        f.main{f.activity!!.finish()};f.activity=null
        assertTrue(PxService.status.value.active);f.waitFor("separate bubble appears outside PX"){PxService.overlayVisibleForTest()}
        f.overlayTouch(MotionEvent.ACTION_DOWN);f.waitFor("background bubble starts voice"){f.enabled.get()==3&&f.microphone};f.overlayTouch(MotionEvent.ACTION_UP);f.waitFor("background release stops voice"){!f.microphone}
        f.stop();f.waitFor("bubble removed after stop"){!PxService.status.value.active&&!PxService.overlayVisibleForTest()};assertFalse(f.microphone)
    }
    @Test fun authorizedChannelStartsLocallyWithoutAnyTalkRequest()=fixture{f->
        f.direct=true;f.join();f.waitFor("PX connects"){PxService.status.value.connected};f.claimGate=CountDownLatch(1)
        f.touch(MotionEvent.ACTION_DOWN);f.waitFor("local microphone starts"){f.microphone};assertEquals("No network floor request on direct PTT",0,f.claims.get())
        f.touch(MotionEvent.ACTION_UP);f.waitFor("local microphone stops"){!f.microphone};assertFalse(PxService.status.value.talking)
        f.touch(MotionEvent.ACTION_DOWN);f.waitFor("second hold starts"){f.enabled.get()==2};f.touch(MotionEvent.ACTION_CANCEL);f.waitFor("cancel stops immediately"){!f.microphone};assertEquals(0,f.claims.get())
    }
    @Test fun releasingBeforeGrantNeverStartsMicrophone()=fixture{f->
        f.join();f.waitFor("PX connects"){PxService.status.value.connected};f.claimGate=CountDownLatch(1)
        f.touch(MotionEvent.ACTION_DOWN);assertTrue("Touch feedback is immediate while authorization is pending",PxService.status.value.requesting);f.waitFor("claim requested"){f.claims.get()>0};f.touch(MotionEvent.ACTION_UP);f.claimGate!!.countDown();Thread.sleep(600);assertEquals(0,f.enabled.get());assertFalse(f.microphone)
    }
    @Test fun focusLossAndAccountChangeStopVoiceAndOverlay()=fixture{f->
        f.join();f.waitFor("PX connects"){PxService.status.value.connected};f.touch(MotionEvent.ACTION_DOWN);f.waitFor("voice starts"){f.microphone};f.main{f.voiceEvent?.invoke("focus-lost","")};f.waitFor("interruption stops PX"){!PxService.status.value.active&&PxService.stoppedForTest()};assertFalse(f.microphone);assertFalse(PxService.overlayVisibleForTest())
        f.join();f.waitFor("PX reconnects by explicit action"){PxService.status.value.connected};f.valid=false;f.main{f.context.getSharedPreferences("user_session",0).edit().putString("access_token","another-test-account").apply()};f.waitFor("account change stops PX"){!PxService.status.value.active};assertFalse(f.microphone)
    }
    private fun fixture(run:(Fixture)->Unit){val f=Fixture();try{f.setup();run(f)}finally{f.cleanup()}}
    private class Fixture {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        var activity:Activity?=null;var valid=true;var direct=false;var microphone=false;val enabled=AtomicInteger();val claims=AtomicInteger();var claimGate:CountDownLatch?=null
        var voiceEvent:((String,String)->Unit)?=null
        private val channel=UUID.randomUUID().toString();private val oldGateway=PxDependencies.gateway;private val oldVoice=PxVoiceDependencies.create;private val oldFriends=FriendsDependencies.gateway
        private val prefs=context.getSharedPreferences("user_session",0);private val original=prefs.all.toMap();private val settings=context.getSharedPreferences("settings",0);val readerSetting=settings.getBoolean("reader_paused",false)
        fun main(block:()->Unit)=inst.runOnMainSync(block)
        fun waitFor(message:String,test:()->Boolean){val end=SystemClock.elapsedRealtime()+12000;while(!test()&&SystemClock.elapsedRealtime()<end)Thread.sleep(60);assertTrue(message,test())}
        private fun shell(cmd:String){android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
        private fun find(view:View,label:String):Button?{if(view is Button&&view.text.toString()==label)return view;if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),label)?.let{return it};return null}
        fun setup(){
            main{context.stopService(Intent(context,PxService::class.java))};Thread.sleep(150)
            PxDependencies.gateway={object:PxGateway{override fun currentAccount()=valid;override fun call(action:String,data:JSONObject):JSONObject{if(action=="claim"){claims.incrementAndGet();claimGate?.await(5,TimeUnit.SECONDS)};return if(action=="list")JSONObject().put("ok",true).put("channels",JSONArray().put(JSONObject().put("id",channel).put("name","Douglas").put("kind","private").put("accepted",true)))else JSONObject().put("ok",true).put("direct_ptt",direct).put("url","wss://synthetic.invalid").put("token","synthetic-token").put("name","Douglas").put("can_talk",action=="claim"||action=="tick").put("lease_ms",8000)}}}
            PxVoiceDependencies.create={_,_,event->voiceEvent=event;object:PxVoice{override val mayPublish=true;override suspend fun connect(url:String,token:String){};override suspend fun microphone(enabledNow:Boolean):Boolean{this@Fixture.microphone=enabledNow;if(enabledNow)enabled.incrementAndGet();return true};override fun volumes(muted:Boolean){};override fun close(){this@Fixture.microphone=false}}}
            FriendsDependencies.gateway={_,_->object:FriendsGateway{override fun call(action:String,data:JSONObject)=JSONObject().put("friends",JSONArray())}}
            main{SessionManager(context).saveUser("px-native@example.invalid","synthetic-token",null)}
            shell("pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}");if(android.os.Build.VERSION.SDK_INT>=33)shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}");if(android.os.Build.VERSION.SDK_INT>=31)shell("pm grant ${context.packageName} ${Manifest.permission.BLUETOOTH_CONNECT}");shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
            activity=inst.startActivitySync(Intent(context,PxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));inst.waitForIdleSync()
        }
        fun join(){if(activity==null)activity=inst.startActivitySync(Intent(context,PxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitFor("channel loads"){var found=false;main{found=find(activity!!.window.decorView,"Entrar no canal")!=null};found};main{find(activity!!.window.decorView,"Entrar no canal")!!.performClick()}}
        fun touch(action:Int){main{val button=find(activity!!.window.decorView,"Segure para falar")?:find(activity!!.window.decorView,"Transmitindo • solte para parar")!!;val t=SystemClock.uptimeMillis();val e=MotionEvent.obtain(t,t,action,25f,25f,0);button.dispatchTouchEvent(e);e.recycle()}}
        fun overlayTouch(action:Int){var rect:android.graphics.Rect?=null;main{rect=PxService.overlayRectForTest()};val r=rect?:error("PX overlay missing");val now=SystemClock.uptimeMillis();val event=MotionEvent.obtain(now,now,action,r.centerX().toFloat(),r.centerY().toFloat(),0);event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;assertTrue(inst.uiAutomation.injectInputEvent(event,true));event.recycle()}
        fun screenshot(){Thread.sleep(200);val bitmap=inst.uiAutomation.takeScreenshot()?:error("Screenshot unavailable");val dir=java.io.File(context.filesDir,"screenshots").apply{mkdirs()};java.io.File(dir,"px-central-${context.resources.configuration.screenWidthDp}.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        fun stop(){main{context.stopService(Intent(context,PxService::class.java))}}
        fun cleanup(){val now=SystemClock.uptimeMillis();val cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0f,0f,0);cancel.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;runCatching{inst.uiAutomation.injectInputEvent(cancel,true)};cancel.recycle();claimGate?.countDown();stop();main{activity?.finish()};Thread.sleep(250);PxDependencies.gateway=oldGateway;PxVoiceDependencies.create=oldVoice;FriendsDependencies.gateway=oldFriends;main{prefs.edit().clear().apply();val e=prefs.edit();for((k,v)in original)when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v)};e.apply()}}
    }
}
