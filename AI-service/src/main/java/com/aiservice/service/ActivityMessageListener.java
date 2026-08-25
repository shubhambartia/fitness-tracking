package com.aiservice.service;

import com.aiservice.model.Activity;
import com.aiservice.model.Recommendation;
import com.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService activityAIService;

    private final RecommendationRepository recommendationRepository;

    @RabbitListener(queues = "activity.queue")
    public void processActivityMessage(Activity activity) {
        log.info("Received activity message: {}", activity.getId());
        Recommendation recommendation = activityAIService.generateRecommendation(activity);
        recommendationRepository.save(recommendation);
        log.info("Generated recommendation: {}", recommendation);
    }
}
