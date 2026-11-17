package tn.fst.events.event.commands;

/**
 * « Détails » d'une CommandExecutionException : seule partie de l'erreur (avec le message) qui
 * traverse Axon Server. La classe de l'exception d'origine, elle, ne voyage pas.
 */
public record ErreurCommande(int status, String message) {
}
