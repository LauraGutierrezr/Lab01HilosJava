package co.eci.blacklist.domain;

import co.eci.blacklist.infrastructure.HostBlackListsDataSourceFacade;
import co.eci.blacklist.labs.part2.ThreadLifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class BlacklistChecker {

    private static final Logger logger = Logger.getLogger(BlacklistChecker.class.getName());

    private final HostBlackListsDataSourceFacade facade;
    private final Policies policies;

    public BlacklistChecker(HostBlackListsDataSourceFacade facade, Policies policies) {
        this.facade = Objects.requireNonNull(facade);
        this.policies = Objects.requireNonNull(policies);
    }

    public MatchResult checkHost(String ip, int nThreads) {
        int threshold = policies.getAlarmCount();
        int total = facade.getRegisteredServersCount();

        long start = System.currentTimeMillis();
        List<Integer> matches = Collections.synchronizedList(new ArrayList<>());

         // Diviidmos en segmentos
        int threads = Math.max(1, nThreads);
        int chunk =  total / threads;

        ThreadLifecycle[] workers = new ThreadLifecycle[threads];
        for (int i = 0; i < threads;i++){
            int startIndex = i * chunk;
            //Aqui manejamos el caso cuando N no divide exacto
            int endIndex = (i == threads - 1) ? total : (i+1) * chunk;
            // Creamos N hilos
            workers[i] = new ThreadLifecycle(startIndex, endIndex, ip);
            //Ejecutamos con start
            workers[i].start();
        }

        for (ThreadLifecycle worker : workers){
            try {
                //Esperamos con Join
                worker.join();
                matches.addAll(worker.getOcurrences());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        //Sumamos los resultados
        int found = matches.size();
        int checked = total;

        //Verficiamos coincidencias >= 5
        boolean trustworthy = found < threshold;

        logger.info("Checked blacklists :" + checked + " of " + total);
        if (trustworthy){
            facade.reportAsTrustworthy(ip);
        } else {
            facade.reportAsNotTrustworthy(ip);
        }

        long elapsed = System.currentTimeMillis() - start;
        return new MatchResult(ip,trustworthy, List.copyOf(matches),checked,total, elapsed,threads);
    }

}
