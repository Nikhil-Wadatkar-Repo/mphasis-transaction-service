package com.mphasis.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mphasis.entity.TransactionRecordEntity;
import com.mphasis.model.TransactionStatus;
import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepo
		extends
			JpaRepository<TransactionRecordEntity, String> {

	java.util.List<TransactionRecordEntity> findByAccountIdAndStatusOrderBySequenceNumberAsc(
			String accountId, TransactionStatus status);

	List<TransactionRecordEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
			TransactionStatus status, Instant dueAt);

	List<TransactionRecordEntity> findByStatusOrderByReceivedAtAsc(TransactionStatus status);

	boolean existsByAccountIdAndSequenceNumber(String accountId, long sequenceNumber);
}
