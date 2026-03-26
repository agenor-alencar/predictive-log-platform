package com.logplatform.repository;

import com.logplatform.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório JPA para a entidade Prediction.
 * 
 * Teoria para aula:
 * - JpaRepository: Interface que o Spring "mágicamente" implementa para nós, 
 *   fornecendo Save, Delete, Find, etc., sem precisarmos escrever SQL manual.
 */
@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    // Derived Query: O Spring cria o SQL "SELECT count(*) FROM predictions WHERE prediction_type = ?" 
    // baseado apenas no nome deste método.
    long countByPredictionType(String predictionType);
}
