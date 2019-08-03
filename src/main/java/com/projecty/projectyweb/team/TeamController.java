package com.projecty.projectyweb.team;

import com.projecty.projectyweb.configurations.AnyPermission;
import com.projecty.projectyweb.configurations.EditPermission;
import com.projecty.projectyweb.misc.RedirectMessage;
import com.projecty.projectyweb.project.Project;
import com.projecty.projectyweb.project.ProjectValidator;
import com.projecty.projectyweb.team.role.NoManagersInTeamException;
import com.projecty.projectyweb.team.role.TeamRole;
import com.projecty.projectyweb.team.role.TeamRoleRepository;
import com.projecty.projectyweb.team.role.TeamRoleService;
import com.projecty.projectyweb.user.User;
import com.projecty.projectyweb.user.UserService;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.*;

import static com.projecty.projectyweb.configurations.AppConfig.REDIRECT_MESSAGES_FAILED;

@Controller
@RequestMapping("team")
public class TeamController {
    private final TeamValidator teamValidator;
    private final TeamRepository teamRepository;
    private final TeamService teamService;
    private final UserService userService;
    private final ProjectValidator projectValidator;
    private final TeamRoleService teamRoleService;
    private final TeamRoleRepository teamRoleRepository;
    private final MessageSource messageSource;

    public TeamController(TeamValidator teamValidator, TeamRepository teamRepository, UserService userService, TeamService teamService, ProjectValidator projectValidator, TeamRoleService teamRoleService, TeamRoleRepository teamRoleRepository, MessageSource messageSource) {
        this.teamValidator = teamValidator;
        this.teamRepository = teamRepository;
        this.userService = userService;
        this.teamService = teamService;
        this.projectValidator = projectValidator;
        this.teamRoleService = teamRoleService;
        this.teamRoleRepository = teamRoleRepository;
        this.messageSource = messageSource;
    }

    @GetMapping("addTeam")
    public ModelAndView addTeam() {
        return new ModelAndView("fragments/team/add-team", "team", new Team());
    }

    @PostMapping("addTeam")
    public String addTeamPost(
            @Valid @ModelAttribute Team team,
            @RequestParam(required = false) List<String> usernames,
            BindingResult bindingResult
    ) {
        teamValidator.validate(team, bindingResult);
        if (bindingResult.hasErrors()) {
            return "fragments/team/add-team";
        }
        List<RedirectMessage> redirectMessages = new ArrayList<>();
        teamService.createTeamAndSave(team, usernames, redirectMessages);
        return "redirect:/team/myTeams";
    }

    @GetMapping("myTeams")
    public ModelAndView myTeams() {
        return new ModelAndView(
                "fragments/team/my-teams",
                "teamRoles",
                userService.getCurrentUser().getTeamRoles());
    }

    @GetMapping("addTeamProject")
    public ModelAndView addProjectForTeam() {
        User current = userService.getCurrentUser();
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("fragments/team/add-project-team");
        Project project = new Project();
        modelAndView.addObject("project", project);
        modelAndView.addObject("teamRoles", teamRoleService.getTeamRolesWhereManager(current));
        return modelAndView;
    }

    @GetMapping(value = "addTeamProject", params = "teamId")
    @EditPermission
    public ModelAndView addProjectForSpecifiedTeamPost(
            @RequestParam Long teamId
    ) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("fragments/team/add-project-specified-team");
        modelAndView.addObject("team", optionalTeam.get());
        modelAndView.addObject("project", new Project());
        return modelAndView;
    }

    @PostMapping("addTeamProject")
    public String addProjectTeamPost(
            @Valid @ModelAttribute Project project,
            @RequestParam Long teamId,
            BindingResult bindingResult
    ) {
        projectValidator.validate(project, bindingResult);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (bindingResult.hasErrors()) {
            return "fragments/team/add-project-team";
        } else if (optionalTeam.isPresent() && teamRoleService.isCurrentUserTeamManager(optionalTeam.get())) {
            teamService.createProjectForTeam(optionalTeam.get(), project);
            return "redirect:/team/myTeams";
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @GetMapping("manageTeam")
    @EditPermission
    public ModelAndView manageTeam(
            @RequestParam Long teamId
    ) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("fragments/team/manage-team");
        modelAndView.addObject("team", optionalTeam.get());
        modelAndView.addObject("currentUser", userService.getCurrentUser());
        return modelAndView;
    }

    @PostMapping("changeName")
    @EditPermission
    public String changeNamePost(
            @RequestParam Long teamId,
            @RequestParam String newName,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        teamService.changeTeamName(optionalTeam.get(), newName);
        redirectAttributes.addAttribute("teamId", teamId);
        return "redirect:/team/manageTeam";
    }

    @PostMapping("addUsers")
    @EditPermission
    public String addUsersPost(
            @RequestParam Long teamId,
            @RequestParam(required = false) List<String> usernames,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        teamRoleService.addTeamMembersByUsernames(optionalTeam.get(), usernames, null);
        teamRepository.save(optionalTeam.get());
        redirectAttributes.addAttribute("teamId", teamId);
        return "redirect:/team/manageTeam";
    }

    @PostMapping("deleteTeamRole")
    @EditPermission
    public String deleteTeamRole(
            @RequestParam Long teamId,
            @RequestParam Long teamRoleId,
            RedirectAttributes redirectAttributes
    ) {
        // TODO: 6/28/19 Prevent from delete current user from team
        Optional<TeamRole> optionalTeamRole = teamRoleRepository.findById(teamRoleId);
        if (optionalTeamRole.isPresent()) {
            teamRoleRepository.delete(optionalTeamRole.get());
            redirectAttributes.addAttribute("teamId", teamId);
            return "redirect:/team/manageTeam";
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @PostMapping("changeTeamRole")
    @EditPermission
    public String changeTeamRolePost(
            @RequestParam Long teamId,
            @RequestParam Long teamRoleId,
            @RequestParam String newRoleName,
            RedirectAttributes redirectAttributes
    ) {
        Optional<TeamRole> optionalTeamRole = teamRoleRepository.findById(teamRoleId);
        if (optionalTeamRole.isPresent()) {
            teamRoleService.changeTeamRole(optionalTeamRole.get(), newRoleName);
            redirectAttributes.addAttribute("teamId", teamId);
            return "redirect:/team/manageTeam";
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @GetMapping("projectList")
    @AnyPermission
    public ModelAndView projectList(@RequestParam Long teamId) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("fragments/team/project-list");
        modelAndView.addObject("team", optionalTeam.get());
        modelAndView.addObject("projects", optionalTeam.get().getProjects());
        return modelAndView;
    }

    @GetMapping("deleteTeamConfirm")
    @EditPermission
    public ModelAndView deleteTeamConfirm(@RequestParam Long teamId) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        return new ModelAndView("fragments/team/delete-team-confirm", "team", optionalTeam.get());
    }

    @PostMapping("deleteTeam")
    @EditPermission
    public String deleteTeamPost(@RequestParam Long teamId) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        teamRepository.delete(optionalTeam.get());
        return "redirect:/team/myTeams";
    }

    @GetMapping("leaveTeamConfirm")
    @AnyPermission
    public ModelAndView leaveTeamConfirm(@RequestParam Long teamId) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        return new ModelAndView("fragments/team/leave-team-confirm", "team", optionalTeam.get());
    }

    @PostMapping("leaveTeam")
    @AnyPermission
    public String leaveTeamPost(
            Long teamId,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        User current = userService.getCurrentUser();
        try {
            teamRoleService.leaveTeam(optionalTeam.get(), current);
        } catch (NoManagersInTeamException e) {
            redirectAttributes.addFlashAttribute(REDIRECT_MESSAGES_FAILED,
                    Collections.singletonList(
                            messageSource.getMessage("team.no_managers.exception",
                                    null,
                                    Locale.getDefault())));
        }
        return "redirect:/team/myTeams";
    }
}
