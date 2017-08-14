package com.smile.calendar.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;

import com.smile.calendar.R;
import com.smile.calendar.baseadapter.BaseRecyclerViewAdapter;
import com.smile.calendar.databinding.ActivityCalendarScheduleBinding;
import com.smile.calendar.databinding.ItemScheduleBinding;
import com.smile.calendar.manager.CalendarManager;
import com.smile.calendar.module.EventModel;
import com.smile.calendar.module.Month;
import com.smile.calendar.util.CalendarUtils;
import com.smile.calendar.util.RealmHelper;
import com.smile.calendar.view.CollapseCalendarView;

import org.joda.time.LocalDate;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;


/**
 * 同步手机系统日程展示
 * Created by SmileXie on 2017/5/8.
 */

public class SyncSystemEventActivity extends BaseActivity<ActivityCalendarScheduleBinding> {

    private LocalDate selectedDate;//当前选择的日期
    private boolean monthChanged = false;//是否切换了周号
    private CalendarManager mManager;
    private CollapseCalendarView calendarView;
    private RecyclerView recyclerView;
    private BaseRecyclerViewAdapter<EventModel, ItemScheduleBinding> adapter;
    private SparseArray<List<EventModel>> eventMap;
    private String startTime, endTime;
    private String title = "null", author = "null", bigType = "null";
    private boolean isFirst = true;//判断是否是第一次进入，如果第一次进入应该都要重新请求的，因为不知道这期间有没有发布新内容
    private List<EventModel> calendarEvent;
    private MyHandler handler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        CollapseCalendarView.withMonthSchedule = false;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar_schedule);
        calendarView = (CollapseCalendarView) findViewById(R.id.calendar);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        initCalendarListener();
        handler = new MyHandler(this);
        initAdapter();
        setRightTitle("同步手机日历事件");
    }

    @Override
    protected void rightClick() {
        startActivity(SyncSystemCalendarEvent.class);
    }

    /**
     * 初始化列表的adapter适配器
     */
    private void initAdapter() {
        adapter = new BaseRecyclerViewAdapter<EventModel, ItemScheduleBinding>(R.layout.item_schedule) {
            @Override
            public void onNewBindViewHolder(EventModel object, int position, ItemScheduleBinding binding) {
                binding.tvScheduleTitle.setText(object.getName() + "-" + object.getF1());
                binding.tvScheduleTime.setText(RealmHelper.MODULE_NOTICE);
            }
        };
        bindingView.recyclerView.setLayoutManager(new LinearLayoutManager(SyncSystemEventActivity.this));
        bindingView.recyclerView.setAdapter(adapter);
    }

    /**
     * 切换每一天时，日程列表跟着变化
     */
    private void changeDayEvent() {
        if (adapter != null && adapter.getData() != null && adapter.getData().size() > 0) {
            adapter.clear();
        }
        if (eventMap != null) {
            List<EventModel> curretDayEvents = eventMap.get(selectedDate.getDayOfMonth());
            if (curretDayEvents != null && curretDayEvents.size() > 0) {
                adapter.addAll(curretDayEvents);
            }
        }
    }

    /**
     * 初始化日程控件
     */
    private void initCalendarListener() {
        selectedDate = LocalDate.now();
        mManager = new CalendarManager(LocalDate.now(),
                CalendarManager.State.MONTH, LocalDate.now().withYear(100),
                LocalDate.now().plusYears(60));
        //月份切换监听器
        mManager.setMonthChangeListener(new CalendarManager.OnMonthChangeListener() {

            @Override
            public void monthChange(String month, LocalDate mSelected) {
                Month currentMonth = (Month) bindingView.calendar.getManager().getUnits();
                startTime = currentMonth.getFrom().toString();
                endTime = currentMonth.getTo().toString();
                setTitle(month);
                monthChanged = true;
                selectedDate = mSelected;
                //当月通知Map清空
                eventMap = new SparseArray<>();
                if (isFirst) {//应用首次进来，先组装已有数据，再删除本月数据，并组装新数据
                    compositeEventToMonthDay();
                    RealmHelper.getRealmHelperInstance().deleteMonthEvent(mSelected.getYear(), mSelected.getMonthOfYear());
                }

                if (CalendarUtils.requestPermission(SyncSystemEventActivity.this)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                List<EventModel> calendarEvent = CalendarUtils.getCalendarEvent(SyncSystemEventActivity.this, mSelected.getYear(), mSelected.getMonthOfYear());
                                if (calendarEvent != null && calendarEvent.size() > 0) {
                                    eventToMap(calendarEvent);
                                }
                                handler.sendEmptyMessage(0);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            }

            @Override
            public void weekChange(String week, LocalDate mSelected) {//周切换

            }
        });
        calendarView.init(mManager);
        /**
         * 日期选中监听器
         */
        calendarView.setDateSelectListener(new CollapseCalendarView.OnDateSelect() {

            @Override
            public void onDateSelected(LocalDate date) {
                selectedDate = date;
                if (monthChanged) {
                    monthChanged = false;
                } else {
                    changeDayEvent();
                }
            }
        });

    }



    /**
     * 组装请求出的通知数据，放至日程中进行显示
     */
    private void compositeEventToMonthDay() {
        List<EventModel> eventModels = RealmHelper.getRealmHelperInstance().queryEventByMonth(selectedDate.getYear(), selectedDate.getMonthOfYear(), RealmHelper.MODULE_NOTICE);
        eventToMap(eventModels);
        notifyInterface();
    }

    private void eventToMap(List<EventModel> eventModels) {
        if (eventModels != null && eventModels.size() > 0) {
            for (EventModel eventModel : eventModels) {
                int key = eventModel.getDay();
                List<EventModel> addModules = new ArrayList<>();
                addModules.add(eventModel);
                List<EventModel> key_modules = eventMap.get(key);
                if (key_modules != null && key_modules.size() > 0) {
                    addModules.addAll(key_modules);
                }
                eventMap.put(key, addModules);
            }
        }
    }

    private void notifyInterface() {
        if (eventMap != null && eventMap.size() > 0) {
            StringBuilder keyBuilder = new StringBuilder();
            for (int i = 0; i < eventMap.size(); i++) {
                int key = eventMap.keyAt(i);
                int selectMonth = selectedDate.getMonthOfYear();
                String month = selectMonth < 10 ? "0" + selectMonth : String.valueOf(selectMonth);
                String day = key < 10 ? "0" + key : String.valueOf(key);
                keyBuilder.append(selectedDate.getYear()).append("-")
                        .append(month).append("-").append(day).append(",");
            }
            calendarView.addMarks(keyBuilder.toString());
        } else {
            calendarView.clearMarks();
        }
        changeDayEvent();
    }


    public static boolean compare_hhmmss(LocalDate DATE1, LocalDate DATE2) throws ParseException {
        return DATE1.isAfter(DATE2);
    }

    private static class MyHandler extends Handler {
        private WeakReference<SyncSystemEventActivity> addDeleteActivityWeakReference;

        public MyHandler(SyncSystemEventActivity addDeleteActivity) {
            addDeleteActivityWeakReference = new WeakReference<SyncSystemEventActivity>(addDeleteActivity);
        }
        @Override
        public void handleMessage(Message msg) {
            SyncSystemEventActivity addDeleteActivity = addDeleteActivityWeakReference.get();
            super.handleMessage(msg);
            if (msg.what == 0) {
                addDeleteActivity.notifyInterface();
            }
        }
    }

}
