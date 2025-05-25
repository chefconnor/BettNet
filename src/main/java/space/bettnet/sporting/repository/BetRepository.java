package space.bettnet.sporting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import space.bettnet.sporting.model.Bet;
import space.bettnet.sporting.model.Bet.BetStatus;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {

    List<Bet> findByStatus(BetStatus status);

    List<Bet> findBySportEventId(String sportEventId);

    @Query("SELECT b FROM Bet b WHERE b.placedAt BETWEEN ?1 AND ?2")
    List<Bet> findBetsInTimeWindow(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(b.returnAmount - b.stake) FROM Bet b WHERE b.status = 'WON' AND b.settledAt BETWEEN ?1 AND ?2")
    Double calculateProfitInTimeWindow(LocalDateTime start, LocalDateTime end);

    @Query("SELECT b FROM Bet b WHERE b.status = 'PENDING' AND b.sportEvent.startTime < ?1")
    List<Bet> findPendingBetsWithStartedEvents(LocalDateTime currentTime);

    @Query("SELECT b.modelVersion, COUNT(b), SUM(CASE WHEN b.status = 'WON' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN b.status = 'LOST' THEN 1 ELSE 0 END), " +
           "SUM(b.returnAmount - b.stake) " +
           "FROM Bet b WHERE b.settledAt IS NOT NULL AND b.status IN ('WON', 'LOST') " +
           "GROUP BY b.modelVersion")
    List<Object[]> getModelPerformanceStats();
}
