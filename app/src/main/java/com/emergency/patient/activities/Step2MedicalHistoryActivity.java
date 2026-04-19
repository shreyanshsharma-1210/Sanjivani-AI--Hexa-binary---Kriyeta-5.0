package com.emergency.patient.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.emergency.patient.R;
import com.emergency.patient.models.PatientProfile;

public class Step2MedicalHistoryActivity extends AppCompatActivity {

    private PatientProfile profile;

    private TextView tvProgress, tvQuestion;
    private RadioGroup rgOptions;
    private RadioButton rbA, rbB, rbC, rbD, rbE;
    private EditText etOther;
    private Button btnNext, btnBack;

    private int currentQuestionIndex = 0;
    private final int TOTAL_QUESTIONS = 5;

    private final String[] questions = {
            "Which category encompasses your most significant past medical diagnosis?",
            "What is your current active pharmacological status?",
            "Which classification best describes your known clinical allergies?",
            "Which hereditary condition is most prevalent among your immediate biological relatives?",
            "Which primary lifestyle factor currently dominates your daily routine?"
    };

    private final String[][] options = {
            {"A. No history of chronic illness or major medical events", "B. Cardiovascular or respiratory conditions", "C. Endocrine or metabolic disorders", "D. Previous major surgeries or prolonged hospitalizations", "Other"},
            {"A. No active daily medications", "B. Occasional use of nonprescription supplements or relief aids", "C. Daily prescription for a single chronic condition", "D. Multiple daily prescriptions for complex management", "Other"},
            {"A. No known allergies to drugs, food, or environment", "B. Mild seasonal or environmental sensitivities", "C. Documented food allergies requiring dietary restriction", "D. Severe adverse reactions to specific pharmaceutical drugs", "Other"},
            {"A. No known significant family history of chronic disease", "B. Cardiovascular disease or early cardiac events", "C. Endocrine or metabolic disorders including diabetes", "D. Documented oncological or autoimmune history", "Other"},
            {"A. Regular physical activity with a managed diet", "B. Sedentary routine with minimal exercise", "C. Frequent tobacco use or high alcohol consumption", "D. High physical or psychological occupational stress", "Other"}
    };

    private final String[] answers = new String[5];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step2_medical_history);

        if (getIntent() != null && getIntent().hasExtra("profile_data")) {
            profile = (PatientProfile) getIntent().getSerializableExtra("profile_data");
        } else {
            profile = new PatientProfile();
        }

        bindViews();
        setupListeners();
        updateQuestionUI();
    }

    private void bindViews() {
        tvProgress = findViewById(R.id.tv_quiz_progress);
        tvQuestion = findViewById(R.id.tv_question_text);
        rgOptions = findViewById(R.id.rg_options);
        rbA = findViewById(R.id.rb_option_a);
        rbB = findViewById(R.id.rb_option_b);
        rbC = findViewById(R.id.rb_option_c);
        rbD = findViewById(R.id.rb_option_d);
        rbE = findViewById(R.id.rb_option_e);
        etOther = findViewById(R.id.et_other_details);
        btnNext = findViewById(R.id.btn_next_quiz);
        btnBack = findViewById(R.id.btn_back_quiz);
    }

    private void setupListeners() {
        rgOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_option_e) {
                etOther.setVisibility(View.VISIBLE);
            } else {
                etOther.setVisibility(View.GONE);
                etOther.setText("");
            }
        });

        btnBack.setOnClickListener(v -> {
            if (currentQuestionIndex > 0) {
                currentQuestionIndex--;
                updateQuestionUI();
            } else {
                finish();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (validateCurrentAnswer()) {
                saveCurrentAnswer();
                if (currentQuestionIndex < TOTAL_QUESTIONS - 1) {
                    currentQuestionIndex++;
                    updateQuestionUI();
                } else {
                    finalizeQuiz();
                }
            }
        });
    }

    private void updateQuestionUI() {
        tvProgress.setText("Question " + (currentQuestionIndex + 1) + " of " + TOTAL_QUESTIONS);
        tvQuestion.setText(questions[currentQuestionIndex]);
        rbA.setText(options[currentQuestionIndex][0]);
        rbB.setText(options[currentQuestionIndex][1]);
        rbC.setText(options[currentQuestionIndex][2]);
        rbD.setText(options[currentQuestionIndex][3]);
        rbE.setText(options[currentQuestionIndex][4]);

        rgOptions.clearCheck();
        etOther.setVisibility(View.GONE);
        etOther.setText("");

        if (currentQuestionIndex == TOTAL_QUESTIONS - 1) {
            btnNext.setText("Review Summary");
        } else {
            btnNext.setText("Next Question");
        }
    }

    private boolean validateCurrentAnswer() {
        if (rgOptions.getCheckedRadioButtonId() == -1) {
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (rbE.isChecked() && etOther.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Please specify details for 'Other'", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void saveCurrentAnswer() {
        RadioButton selected = findViewById(rgOptions.getCheckedRadioButtonId());
        String answer;
        if (selected.getId() == R.id.rb_option_e) {
            answer = "Other: " + etOther.getText().toString().trim();
        } else {
            answer = selected.getText().toString();
        }
        answers[currentQuestionIndex] = answer;
    }

    private void finalizeQuiz() {
        profile.setPastMedicalDiagnosis(answers[0]);
        profile.setPharmacologicalStatus(answers[1]);
        profile.setClinicalAllergies(answers[2]);
        profile.setHereditaryConditions(answers[3]);
        profile.setLifestyleFactor(answers[4]);

        Intent intent = new Intent(this, Step3VerifyResumeActivity.class);
        intent.putExtra("profile_data", profile);
        startActivity(intent);
    }
}
