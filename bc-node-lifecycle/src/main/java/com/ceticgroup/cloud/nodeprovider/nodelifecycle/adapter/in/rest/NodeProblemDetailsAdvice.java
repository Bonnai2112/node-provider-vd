package com.ceticgroup.cloud.nodeprovider.nodelifecycle.adapter.in.rest;

import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.IllegalNodeTransitionException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.MevBoostAlreadyEnabledException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.MevBoostNotEnabledException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.MevBoostRequiresValidatorException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.NodeNotFoundException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorAlreadyEnabledException;
import com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain.ValidatorNotEnabledException;
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

    @ExceptionHandler(ValidatorAlreadyEnabledException.class)
    ProblemDetail handleValidatorAlreadyEnabled(ValidatorAlreadyEnabledException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Validator already enabled");
        pd.setType(URI.create(PROBLEM_BASE + "validator-already-enabled"));
        return pd;
    }

    @ExceptionHandler(ValidatorNotEnabledException.class)
    ProblemDetail handleValidatorNotEnabled(ValidatorNotEnabledException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Validator not enabled");
        pd.setType(URI.create(PROBLEM_BASE + "validator-not-enabled"));
        return pd;
    }

    @ExceptionHandler(MevBoostAlreadyEnabledException.class)
    ProblemDetail handleMevBoostAlreadyEnabled(MevBoostAlreadyEnabledException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("MEV-Boost already enabled");
        pd.setType(URI.create(PROBLEM_BASE + "mev-boost-already-enabled"));
        return pd;
    }

    @ExceptionHandler(MevBoostNotEnabledException.class)
    ProblemDetail handleMevBoostNotEnabled(MevBoostNotEnabledException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("MEV-Boost not enabled");
        pd.setType(URI.create(PROBLEM_BASE + "mev-boost-not-enabled"));
        return pd;
    }

    @ExceptionHandler(MevBoostRequiresValidatorException.class)
    ProblemDetail handleMevBoostRequiresValidator(MevBoostRequiresValidatorException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("MEV-Boost requires validator");
        pd.setType(URI.create(PROBLEM_BASE + "mev-boost-requires-validator"));
        return pd;
    }
}
