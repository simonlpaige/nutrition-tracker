package com.simon.dailyrhythm;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MealEstimatorTest {
    JSONObject estimate()throws Exception{return new JSONObject("{\"schema\":\"daily-rhythm.recipe.v1\",\"name\":\"Beans\",\"yieldServings\":1,\"portion\":\"one cup cooked\",\"confidence\":\"low\",\"assumptions\":[\"No added fat\"],\"sources\":[],\"perServing\":{\"calories\":220,\"protein\":14,\"carbs\":40,\"fat\":1,\"fiber\":12,\"sugar\":null,\"sodium\":null}}");}
    @Test public void requestExcludesJournalNotesIdentifiersAndServingMultiplication()throws Exception{
        JSONObject meal=new JSONObject().put("name","Beans").put("portion","one cup cooked").put("ingredients","Beans, water").put("notes","PRIVATE NOTE").put("id","PRIVATE ID").put("servings",3).put("sleep",8);
        JSONObject request=MealEstimator.body(meal),input=new JSONObject(request.getString("input"));assertEquals(3,input.length());assertFalse(input.has("id"));assertFalse(input.has("servings"));assertFalse(request.getBoolean("store"));assertTrue(request.getJSONObject("text").getJSONObject("format").getBoolean("strict"));assertFalse(request.toString().contains("PRIVATE"));
        java.nio.file.Files.write(new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"nutrition-request-fixture.json").toPath(),request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void parserAndValidatorRejectIncompleteRefusedOrInvalidEstimates()throws Exception{
        JSONObject e=estimate();MealEstimator.validate(e);JSONObject response=new JSONObject().put("status","completed").put("output",new JSONArray().put(new JSONObject().put("type","message").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",e.toString())))));
        assertEquals(12,MealEstimator.parseResponse(response).getJSONObject("perServing").getInt("fiber"));
        response.put("status","incomplete");assertThrows(Exception.class,()->MealEstimator.parseResponse(response));e.getJSONObject("perServing").put("fiber",-1);assertThrows(Exception.class,()->MealEstimator.validate(e));e.getJSONObject("perServing").put("fiber",12);e.put("yieldServings",4);assertThrows(Exception.class,()->MealEstimator.validate(e));
    }
    @Test public void privateKeyIsEncryptedAndNotReturnedByStatus()throws Exception{
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();MealEstimator estimator=new MealEstimator(c,(type,value)->{});estimator.clear();String fake="sk-fixture-do-not-use";
        try{assertEquals("",estimator.configure(fake,true));assertTrue(estimator.status().getBoolean("configured"));assertTrue(estimator.status().getBoolean("enabled"));assertFalse(estimator.status().toString().contains(fake));assertFalse(c.getSharedPreferences("ai_secure",0).getAll().toString().contains(fake));assertEquals("",estimator.configure("",false));assertFalse(estimator.status().getBoolean("enabled"));}finally{estimator.clear();estimator.close();}
    }
}
