package tn.fst.events.participant.controllers;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tn.fst.events.participant.dto.Mapper;
import tn.fst.events.participant.dto.ParticipantDTO;
import tn.fst.events.participant.entities.Tache;
import tn.fst.events.participant.services.IParticipantServices;

/**
 * API participants. Via la Gateway : /api/participants/** (StripPrefix=1 → /participants/**).
 * Monolithe : /events/participant/add, /all, /{id}, /update/{id}, /{id}/tache/{t}, /delete/{id}.
 */
@RequiredArgsConstructor
@RequestMapping("/participants")
@RestController
public class ParticipantRestController {

    private final IParticipantServices participantServices;

    /** Liste complète, ou lot d'identifiants (?ids=1,2,3) pour les appels Feign. */
    @GetMapping
    public List<ParticipantDTO> getParticipants(@RequestParam(name = "ids", required = false) List<Integer> ids) {
        return Mapper.toDtos(ids == null
                ? participantServices.getAllParticipants()
                : participantServices.getParticipantsByIds(ids));
    }

    @GetMapping("/{id}")
    public ParticipantDTO getParticipant(@PathVariable("id") int idPart) {
        return Mapper.toDto(participantServices.getParticipantById(idPart));
    }

    @GetMapping("/search")
    public List<ParticipantDTO> search(@RequestParam("nom") String nom, @RequestParam("prenom") String prenom,
                                       @RequestParam("tache") Tache tache) {
        return Mapper.toDtos(participantServices.search(nom, prenom, tache));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantDTO addParticipant(@Valid @RequestBody ParticipantDTO participant) {
        return Mapper.toDto(participantServices.addParticipant(Mapper.toEntity(participant)));
    }

    @PutMapping("/{id}")
    public ParticipantDTO updateParticipant(@PathVariable("id") int idPart,
                                            @Valid @RequestBody ParticipantDTO participant) {
        return Mapper.toDto(participantServices.updateParticipant(idPart, Mapper.toEntity(participant)));
    }

    @PutMapping("/{id}/tache/{tache}")
    public ParticipantDTO changeTache(@PathVariable("id") int idPart, @PathVariable("tache") Tache tache) {
        return Mapper.toDto(participantServices.changeTache(idPart, tache));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParticipant(@PathVariable("id") int idPart) {
        participantServices.deleteParticipant(idPart);
    }
}
