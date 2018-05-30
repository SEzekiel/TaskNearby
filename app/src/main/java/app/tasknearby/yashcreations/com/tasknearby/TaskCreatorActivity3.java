package app.tasknearby.yashcreations.com.tasknearby;

import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.touchboarder.weekdaysbuttons.WeekdaysDataItem;
import com.touchboarder.weekdaysbuttons.WeekdaysDataSource;

import java.util.ArrayList;
import java.util.Calendar;

import app.tasknearby.yashcreations.com.tasknearby.utils.WeekdayCodeUtils;

public class TaskCreatorActivity3 extends AppCompatActivity {

    LinearLayout layoutSelectLocation;
    EditText editTextLocation;
    LinearLayout layoutSelectImage;
    ImageView imageSelected;
    ViewStub viewStubRepeat;
    LinearLayout layoutTitleAttachment, layoutTitleSchedule;
    ConstraintLayout layoutContentAttachment, layoutContentSchedule;
    Animation slideUp, slideDown;
    Switch switchRepeat;
    ImageView imageArrowAttachment, imageArrowSchedule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_creator3);

        layoutSelectLocation = findViewById(R.id.layout_select_location);
        editTextLocation = findViewById(R.id.editText_location_name);
        layoutSelectImage = findViewById(R.id.layout_select_image);
        imageSelected = findViewById(R.id.image_selected_image);
        viewStubRepeat = findViewById(R.id.viewStub_repeat);
        layoutContentAttachment = findViewById(R.id.layout_content_attachment);
        layoutContentSchedule = findViewById(R.id.layout_content_schedule);
        layoutTitleAttachment = findViewById(R.id.layout_title_attachment);
        layoutTitleSchedule = findViewById(R.id.layout_title_schedule);
        slideUp = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_up);
        slideDown = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.slide_down);
        switchRepeat = findViewById(R.id.switch_repeat);
        imageArrowAttachment = findViewById(R.id.image_arrow_attachment);
        imageArrowSchedule = findViewById(R.id.image_arrow_schedule);

        switchRepeat.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewStubRepeat.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        setupWeekdayBar();

        layoutSelectLocation.setOnClickListener(v -> {
            editTextLocation.setVisibility(View.VISIBLE);
        });

        layoutSelectImage.setOnClickListener(v -> {
            imageSelected.setVisibility(View.VISIBLE);
        });

        layoutTitleAttachment.setOnClickListener(v -> {
            if(layoutContentAttachment.getVisibility() == View.GONE) {
                layoutContentAttachment.setVisibility(View.VISIBLE);
                layoutContentAttachment.setAnimation(slideDown);
                imageArrowAttachment.setImageResource(R.drawable.ic_arrow_up_grey_24dp);

            } else {
                layoutContentAttachment.setVisibility(View.GONE);
                layoutContentAttachment.setAnimation(slideUp);
                imageArrowAttachment.setImageResource(R.drawable.ic_arrow_down_black_24dp);
            }

        });

        layoutTitleSchedule.setOnClickListener(v -> {
            if(layoutContentSchedule.getVisibility() == View.GONE) {
                layoutContentSchedule.setVisibility(View.VISIBLE);
                layoutContentSchedule.setAnimation(slideDown);
                imageArrowSchedule.setImageResource(R.drawable.ic_arrow_up_grey_24dp);

            } else {
                layoutContentSchedule.setVisibility(View.GONE);
                layoutContentSchedule.setAnimation(slideUp);
                imageArrowSchedule.setImageResource(R.drawable.ic_arrow_down_black_24dp);
            }

        });
    }

    private void setupWeekdayBar() {
        // Assumption: No day is selected initially.
        viewStubRepeat.setTag(0);
        WeekdaysDataSource wds = new WeekdaysDataSource(this, R.id.viewStub_repeat)
                .setFirstDayOfWeek(Calendar.MONDAY)
                .setUnselectedColorRes(R.color.dark_grey)
                .start(new WeekdaysDataSource.Callback() {
                    /**
                     * Called every time an item is clicked (selected or deselected).
                     * @param weekdaysDataItem calling getCalendarDayId() on this returns the
                     * day's index as in Java Calendar API. Sunday = 1, Monday = 2....
                     */
                    @Override
                    public void onWeekdaysItemClicked(int i, WeekdaysDataItem weekdaysDataItem) {
                        int dayCode = WeekdayCodeUtils
                                .getDayCodeByCalendarDayId(weekdaysDataItem.getCalendarDayId());
                        int selection = (int) viewStubRepeat.getTag();
                        // Doing an XOR here so that if tapped again, then the day is removed.
                        selection ^= dayCode;
                        viewStubRepeat.setTag(selection);
                        Log.d("Shilpi", "Selected days : " + selection);
                    }

                    @Override
                    public void onWeekdaysSelected(int i, ArrayList<WeekdaysDataItem> arrayList) {
                    }
                });
        // Need to explicitly make it GONE in code.
        viewStubRepeat.setVisibility(View.GONE);
    }
}
