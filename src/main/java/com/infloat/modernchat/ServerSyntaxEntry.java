package com.infloat.modernchat;

/**
 * One entry representing a server syntax file discovered in the
 * server_syntaxes/ directory on GitHub. Populated by SyntaxFetcher.fetchDirectory().
 */
public class ServerSyntaxEntry {
    public String name;
    public String key;
    public String ip;
    public String url;
}
