package com.example.gradproj.EduNest.controller.weeks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.weeks.CreateWeekrequest;
import com.example.gradproj.EduNest.dto.weeks.UpdateWeekRequest;
import com.example.gradproj.EduNest.dto.weeks.WeekResponse;
import com.example.gradproj.EduNest.service.week.WeekService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/week")
@RequiredArgsConstructor
public class WeekController {
    private final WeekService weekService;

    @PostMapping("/create")
    public ResponseEntity<SimpleResponse>create(@RequestBody CreateWeekrequest createWeekrequest){
        WeekResponse create=weekService.createWeek(createWeekrequest);
        SimpleResponse response=new SimpleResponse();
        response.addMessage("message","week created successfully");
        response.addMessage("week",create);
        return  ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<SimpleResponse>delete(@PathVariable Long id){
        weekService.deleteWeek(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message", "week deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @PatchMapping("/{id}")
    public ResponseEntity<SimpleResponse>update(@PathVariable Long id, @RequestBody UpdateWeekRequest updateWeekRequest) {
        WeekResponse update = weekService.udateWeekTitle(id, updateWeekRequest);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "week updated successfully");
        response.addMessage("week",update);
        return   ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{mentorshipId}/weeks")
    public ResponseEntity<SimpleResponse> getWeeksByMentorship(Long mentorshipId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status","all weeks retrieved successfully");
        response.addMessage("weeks",weekService.getWeeksByMentorship(mentorshipId));
        return   ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @GetMapping("/{weekId}/contents")
    public ResponseEntity<SimpleResponse>getContents(@PathVariable Long weekId){
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("Status","all weeks retrieved successfully");
        simpleResponse.addMessage("week",weekService.getWeekContents(weekId));
        return   ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

}
