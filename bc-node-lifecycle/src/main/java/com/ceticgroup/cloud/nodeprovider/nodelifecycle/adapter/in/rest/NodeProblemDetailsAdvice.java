package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.IllegalNodeTransitionException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class NodeProblemDetailsAdvice {

    private static final String PROBLEM_BASE = "https://api.cetic-group.com/problems/";

    @ExceptionHandler(NodeNotFoundException.class)
    ProblemDetail handleNotFound(NodeNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Node not found");
        pd.setType(URI.create(PROBLEM_BASE + "node-not-found"));
        pd.setProperty("nodeId", ex.id().value());
        return pd;
    }

    @ExceptionHandler(IllegalNodeTransitionException.class)
    ProblemDetail handleIllegalTransition(IllegalNodeTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Illegal node transition");
        pd.setType(URI.create(PROBLEM_BASE + "illegal-node-transition"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid request");
        pd.setType(URI.create(PROBLEM_BASE + "invalid-request"));
        return pd;
    }
}
