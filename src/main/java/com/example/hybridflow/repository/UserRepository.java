package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.AuthProvider;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

        Optional<User> findByEmail(String email);

        Optional<User> findByTeamAndRole(Team team, Role role);

        @Query("""
                            select u from User u
                            left join fetch u.company
                            where u.email in :emails
                        """)
        List<User> findByEmailInWithCompany(@Param("emails") java.util.Collection<String> emails);

        Optional<User> findByProviderAndProviderId(
                        AuthProvider provider,
                        String providerId);

        // Find all employees in a team
        // List<User> findByTeamId(Long teamId);

        List<User> findByTeamIdIn(List<Long> teamIds);

        List<User> findAllByTeamId(Long teamId);

        boolean existsByEmail(String email);

        @Query("""
                            select u from User u
                            left join fetch u.company
                            left join fetch u.team t
                            left join fetch t.office
                            where u.email = :email
                        """)
        Optional<User> findAuthContextByEmail(@Param("email") String email);

        @Query("""
                            select count(u)
                            from User u
                            where u.team.id = :teamId
                              and u.role in (com.example.hybridflow.entity.Role.EMPLOYEE, com.example.hybridflow.entity.Role.MANAGER)
                        """)
        int countSchedulableUsersByTeamId(@Param("teamId") Long teamId);

        // this method is used just for the demos
        @Query("""
                            select u
                            from User u
                            join fetch u.team t
                            where t.id in :teamIds
                              and u.role in (com.example.hybridflow.entity.Role.EMPLOYEE, com.example.hybridflow.entity.Role.MANAGER)
                            order by t.id asc, u.id asc
                        """)
        List<User> findSchedulableUsersByTeamIds(@Param("teamIds") List<Long> teamIds);

        // this method is used for real life scenarios
        // @Query("""
        //                     select u
        //                     from User u
        //                     join fetch u.team t
        //                     where t.id in :teamIds
        //                       and u.enabled = true
        //                       and u.role in (com.example.hybridflow.entity.Role.EMPLOYEE, com.example.hybridflow.entity.Role.MANAGER)
        //                     order by t.id asc, u.id asc
        //                 """)
        // List<User> findSchedulableUsersByTeamIds(@Param("teamIds") List<Long> teamIds);
}