package com.emergency.patient.rag;

import java.util.ArrayList;
import java.util.List;

public class HealthKnowledgeBase {

    public static class QA {
        public String question;
        public String answer;

        public QA(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    private static final List<QA> KNOWLEDGE = new ArrayList<>();

    static {
        // Standardized Context Questions
        KNOWLEDGE.add(new QA("Can I take amoxicillin for an infection?", "No. You are allergic to beta-lactam antibiotics including amoxicillin and penicillin, which may cause rash and urticaria. Doctors should prescribe alternative antibiotics such as azithromycin."));
        KNOWLEDGE.add(new QA("What should I take if I have a mild headache or fever?", "You may take paracetamol 500 mg if needed, with a maximum of four tablets per day unless advised otherwise by a doctor."));
        KNOWLEDGE.add(new QA("Why do I experience sneezing and watery eyes during spring?", "You have seasonal allergic rhinitis triggered by pollen and dust mites, which commonly causes sneezing, nasal congestion, and watery eyes."));
        KNOWLEDGE.add(new QA("Is it safe for me to drink milk or consume dairy products?", "You are lactose intolerant. Dairy products may cause bloating, cramps, or diarrhoea. Use lactase supplements or choose lactose-free alternatives."));
        KNOWLEDGE.add(new QA("Do I have any medication allergies doctors should know about?", "Yes. You are allergic to penicillin, amoxicillin, and other beta-lactam antibiotics, which can cause rash and urticaria."));
        KNOWLEDGE.add(new QA("Is my liver fully recovered after my previous jaundice infection?", "Yes. Your liver function tests are normal, confirming full recovery from Hepatitis A."));
        KNOWLEDGE.add(new QA("Are my blood test results normal?", "Yes. Your CBC, liver function, glucose, kidney function, and lipid profile results are all within normal ranges."));
        KNOWLEDGE.add(new QA("Do I have any vitamin deficiencies?", "Your Vitamin D level is slightly low at 28 ng/mL. Daily sunlight exposure and Vitamin D3 supplementation are recommended."));
        KNOWLEDGE.add(new QA("Am I at risk of diabetes based on my family history?", "Your grandfather has type 2 diabetes, so regular monitoring is advisable even though your current glucose levels are normal."));
        KNOWLEDGE.add(new QA("What key medical information should doctors know in an emergency?", "Your blood group is B+, you are allergic to beta-lactam antibiotics, you have lactose intolerance, and you have seasonal allergies."));

        // Set 1
        KNOWLEDGE.add(new QA("Can I take any medicine for a cold without checking first?", "You should avoid medicines containing penicillin-type antibiotics because you are allergic to them."));
        KNOWLEDGE.add(new QA("If I feel weak after working out, what could be the reason?", "Your Vitamin D level is slightly low, so more sunlight exposure or Vitamin D3 supplements may help."));
        KNOWLEDGE.add(new QA("Do I have any food that I should avoid regularly?", "Yes. Dairy products like milk, cheese, and ice cream may cause stomach discomfort because you are lactose intolerant."));
        KNOWLEDGE.add(new QA("Is it safe for me to take medicines that contain dairy ingredients?", "It is better to avoid medicines that contain lactose since they may cause digestive issues for you."));
        KNOWLEDGE.add(new QA("Why do my eyes get watery and my nose blocked sometimes?", "This likely happens due to your seasonal environmental allergies triggered by pollen and dust."));
        KNOWLEDGE.add(new QA("If I feel stomach discomfort after eating certain foods, what might be the cause?", "It may be due to lactose intolerance, especially if you consumed dairy products."));
        KNOWLEDGE.add(new QA("Do I need regular health checkups even if I feel healthy?", "Yes. A routine health check once a year is recommended to monitor overall health."));
        KNOWLEDGE.add(new QA("Should I be careful about any particular medicines because of my past illness?", "Yes. Since you had Hepatitis A earlier, you should avoid excessive use of medicines that can strain the liver."));
        KNOWLEDGE.add(new QA("Is my heart health normal based on my current records?", "Yes. Your resting heart rate, blood pressure, and overall fitness levels are within normal ranges."));
        KNOWLEDGE.add(new QA("Is there anything doctors should double-check before giving me treatment?", "They should confirm that any prescribed antibiotic is not from the penicillin or beta-lactam group due to your allergy."));

        // Set 2
        KNOWLEDGE.add(new QA("Are there any foods I should avoid in my daily diet?", "You should limit dairy products like milk, cheese, and ice cream because you are lactose intolerant and they may cause stomach discomfort."));
        KNOWLEDGE.add(new QA("What should I do if I catch a cold during winter?", "Stay hydrated, rest well, and use antihistamines like cetirizine if symptoms are related to your seasonal allergies."));
        KNOWLEDGE.add(new QA("Why do I feel uncomfortable in very cold or dry weather?", "You have mild sensitivity to cold or dry air, which can cause throat dryness and nasal irritation."));
        KNOWLEDGE.add(new QA("What foods should I be careful about when eating outside?", "Avoid dairy-heavy foods such as creamy sauces, milk-based desserts, or cheese-rich dishes since they may trigger lactose intolerance symptoms."));
        KNOWLEDGE.add(new QA("What can I do if pollen season makes me uncomfortable?", "Limit outdoor exposure during high pollen days and use antihistamines like cetirizine when symptoms appear."));
        KNOWLEDGE.add(new QA("Is it okay for me to drink milkshakes or dairy-based smoothies?", "It is better to avoid them unless you take a lactase supplement, as dairy may cause digestive issues."));
        KNOWLEDGE.add(new QA("What should I do if I feel nasal congestion or sneezing during weather changes?", "Using saline nasal spray and antihistamines can help relieve symptoms caused by your seasonal allergies."));
        KNOWLEDGE.add(new QA("Are there lifestyle habits I should maintain to stay healthy?", "Continue regular physical activity, maintain hydration, and get regular sunlight exposure to support Vitamin D levels."));
        KNOWLEDGE.add(new QA("What should I do if my throat feels dry in cold weather?", "Drink warm fluids, stay hydrated, and avoid prolonged exposure to very cold or dry air."));
        KNOWLEDGE.add(new QA("Is there there anything I should keep in mind when travelling to colder places?", "Carry allergy medication like cetirizine and protect yourself from cold, dry air which may trigger mild nasal irritation."));
    }

    public static List<QA> getAllKnowledge() {
        return KNOWLEDGE;
    }

    /**
     * Finds the most relevant Q&A pairs based on keyword matching.
     */
    public static String getRelevantContext(String query) {
        String[] queryWords = query.toLowerCase().split("[^a-zA-Z0-9]+");
        StringBuilder context = new StringBuilder();
        
        List<QAWithScore> scored = new ArrayList<>();
        for (QA qa : KNOWLEDGE) {
            int score = 0;
            String qLower = qa.question.toLowerCase();
            for (String word : queryWords) {
                if (word.length() > 2 && qLower.contains(word)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new QAWithScore(qa, score));
            }
        }

        scored.sort((a, b) -> b.score - a.score);

        for (int i = 0; i < Math.min(5, scored.size()); i++) {
            QA qa = scored.get(i).qa;
            context.append("Q: ").append(qa.question).append("\nA: ").append(qa.answer).append("\n\n");
        }

        return context.toString();
    }

    private static class QAWithScore {
        QA qa;
        int score;
        QAWithScore(QA qa, int score) {
            this.qa = qa;
            this.score = score;
        }
    }
}
