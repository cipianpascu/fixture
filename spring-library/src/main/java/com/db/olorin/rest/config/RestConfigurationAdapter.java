package com.db.olorin.rest.config;

import com.db.olorin.core.configuration.ConfigurationIF;
import com.db.olorin.core.configuration.ConfigurationItemIF;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/** Adapts the application's core configuration section without changing legacy gateway.* keys. */
public final class RestConfigurationAdapter {
  public static final String SECTION_NAME = "com.db.olorin.rest.configuration";
  private static final Pattern BACKEND = Pattern.compile("gateway\\.backends\\[(\\d+)]\\.(.+)");
  private final Map<String,String> values;
  private RestConfigurationAdapter(Map<String,String> values) { this.values=values; }
  public static RestConfigurationAdapter from(ConfigurationIF c) {
    if(c==null || !SECTION_NAME.equals(c.getName())) throw new ProxyConfigurationException("A ConfigurationIF named '"+SECTION_NAME+"' is required");
    Map<String,String> v=new LinkedHashMap<>();
    for(ConfigurationItemIF i:c.getConfigurationItems()) if(i!=null&&i.getName()!=null&&i.getValue()!=null&&v.put(i.getName(),i.getValue())!=null) throw new ProxyConfigurationException("Duplicate configuration item '"+i.getName()+"'");
    if(v.keySet().stream().noneMatch(k->k.startsWith("gateway."))) throw new ProxyConfigurationException("Configuration section '"+SECTION_NAME+"' does not contain gateway.* properties");
    return new RestConfigurationAdapter(Map.copyOf(v));
  }
  public static ProxyProperties bind(RestConfigurationProvider p) { return from(p.configuration()).proxyProperties(); }
  public ProxyProperties proxyProperties() { return new Properties(); }
  /** Raw values are retained for referenced auth-service configuration. */
  public Map<String,String> values(){return values;}
  private String v(String key,String fallback) {
    String value=values.get(key); if(value!=null)return value;
    String alternate=key.contains("-")?key.replace("-",""):key.replaceAll("([a-z])([A-Z])","$1-$2").toLowerCase(Locale.ROOT);
    return values.getOrDefault(alternate,fallback);
  }
  private Optional<String> opt(String k){return Optional.ofNullable(v(k,null)).filter(x->!x.isBlank());}
  private boolean bool(String k,boolean d){return opt(k).map(Boolean::parseBoolean).orElse(d);}
  private int integer(String k,int d){try{return opt(k).map(Integer::parseInt).orElse(d);}catch(NumberFormatException e){throw invalid(k,"integer");}}
  private Duration duration(String k,Duration d){String value=v(k,null);if(value==null)return d;try{return value.matches("\\d+")?Duration.ofMillis(Long.parseLong(value)):Duration.parse(value.startsWith("P")?value:"PT"+value.toUpperCase(Locale.ROOT));}catch(RuntimeException e){throw invalid(k,"duration");}}
  private ProxyConfigurationException invalid(String k,String t){return new ProxyConfigurationException("Configuration item '"+k+"' is not a valid "+t);}
  private boolean has(String prefix){return values.keySet().stream().anyMatch(k->k.startsWith(prefix)||k.startsWith(prefix.substring(0,Math.max(0,prefix.length()-1))+"["));}
  private void required(String k){if(opt(k).isEmpty())throw new ProxyConfigurationException("Required configuration item '"+k+"' is missing");}
  private Map<String,String> map(String prefix){Map<String,String> r=new LinkedHashMap<>();values.forEach((k,v)->{if(k.startsWith(prefix))r.put(k.substring(prefix.length()),v);});return Map.copyOf(r);}
  private List<String> list(String k){
    List<String> result=new ArrayList<>();
    opt(k).ifPresent(x->{for(String value:x.split("\\s*,\\s*"))if(!value.isBlank())result.add(value);});
    Pattern indexed=Pattern.compile(Pattern.quote(k)+"\\[(\\d+)]");
    values.entrySet().stream().map(entry->{Matcher matcher=indexed.matcher(entry.getKey());return matcher.matches()?Map.entry(Integer.parseInt(matcher.group(1)),entry.getValue()):null;})
      .filter(Objects::nonNull).sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).filter(value->value!=null&&!value.isBlank()).forEach(result::add);
    return List.copyOf(result);
  }
  private Optional<List<String>> optionalList(String k){List<String> values=list(k);return values.isEmpty()?Optional.empty():Optional.of(values);}

  private final class Properties implements ProxyProperties {
    public SchemaConfig schemas(){return new Schema();}
    public Optional<TlsConfig> tls(){return has("gateway.tls.")?Optional.of(new Tls()):Optional.empty();}
    public HistoryConfig history(){return new History("gateway.history.");}
    public List<BackendDefinition> backends(){Map<Integer,String> p=new TreeMap<>();values.keySet().forEach(k->{Matcher m=BACKEND.matcher(k);if(m.matches())p.put(Integer.valueOf(m.group(1)),"gateway.backends["+m.group(1)+"].");});if(p.isEmpty())throw new ProxyConfigurationException("No gateway.backends[*] configuration is defined");List<BackendDefinition> r=new ArrayList<>();p.values().forEach(x->r.add(new Backend(x)));r.sort(Comparator.comparing(BackendDefinition::name));return List.copyOf(r);}
  }
  private final class Schema implements ProxyProperties.SchemaConfig {
    public String directory(){return v("gateway.schemas.directory","classpath:schemas/");} public boolean validateRequests(){return bool("gateway.schemas.validate-requests",true);} public boolean validateBodies(){return bool("gateway.schemas.validate-bodies",true);} public boolean validateResponses(){return bool("gateway.schemas.validate-responses",false);} public boolean strictMode(){return bool("gateway.schemas.strict-mode",true);}
  }
  private final class Backend implements ProxyProperties.BackendDefinition {
    private final String p; Backend(String p){this.p=p;required(p+"name");if(opt(p+"baseUrl").isEmpty() && opt(p+"base-url").isEmpty())throw new ProxyConfigurationException("Required configuration item '"+p+"baseUrl' is missing");}
    public String name(){return v(p+"name",null);} public String baseUrl(){return v(p+"baseUrl",v(p+"base-url",null));} public String path(){return v(p+"path","");} public Optional<String> schema(){return opt(p+"schema");} public Duration timeout(){return duration(p+"timeout",Duration.ofSeconds(30));} public boolean enabled(){return bool(p+"enabled",true);} public String protocol(){return v(p+"protocol","rest");} public String httpVersion(){return v(p+"http-version","http1_1");} public Optional<String> securityType(){return opt(p+"securityType").or(()->opt(p+"security-type"));} public Map<String,String> securityConfig(){Map<String,String> r=map(p+"securityConfig.");return r.isEmpty()?map(p+"security-config."):r;} public Optional<ProxyProperties.AuthRequestConfig> authRequest(){return has(p+"auth-request.")?Optional.of(new AuthRequest(p+"auth-request.")):Optional.empty();} public Optional<ProxyProperties.AuthzRequestConfig> authzRequest(){return has(p+"authz-request.")?Optional.of(new AuthzRequest(p+"authz-request.")):Optional.empty();} public Optional<ProxyProperties.BackendHistoryConfig> history(){return has(p+"history.")?Optional.of(new BackendHistory(p+"history.")):Optional.empty();} public Optional<String> tlsProfile(){return opt(p+"tls-profile");} public Optional<ProxyProperties.SoapConfig> soap(){return has(p+"soap.")?Optional.of(new Soap(p+"soap.")):Optional.empty();} public Optional<ProxyProperties.ProxyConfig> proxy(){return has(p+"proxy.")?Optional.of(new Proxy(p+"proxy.")):Optional.empty();} public List<String> forwardHeaders(){return list(p+"forward-headers");} public List<String> forwardCookies(){return list(p+"forward-cookies");}
  }
  private final class AuthRequest implements ProxyProperties.AuthRequestConfig {private final String p;AuthRequest(String p){this.p=p;}public Optional<String> service(){return opt(p+"service");}public Optional<String> path(){return opt(p+"path");}public Optional<List<String>> sparteGvo(){return optionalList(p+"sparte-gvo");}public Optional<List<String>> btx(){return optionalList(p+"btx");}public Optional<List<String>> pss(){return optionalList(p+"pss");}}
  private final class AuthzRequest implements ProxyProperties.AuthzRequestConfig {private final String p;AuthzRequest(String p){this.p=p;}public Optional<String> service(){return opt(p+"service");}public String path(){return v(p+"path","");}public Optional<String> branchCustomerNumber(){return opt(p+"branch-customer-number");}public Optional<List<String>> gvoEntitlementsList(){return optionalList(p+"gvo-entitlements-list");}public Optional<List<String>> businessTransactions(){return optionalList(p+"business-transactions");}public Optional<List<String>> serviceShopTransactions(){return optionalList(p+"service-shop-transactions");}}
  private final class History implements ProxyProperties.HistoryConfig {private final String p;History(String p){this.p=p;}public boolean enabled(){return bool(p+"enabled",false);}public String provider(){return v(p+"provider","gcp-pubsub");}public String deliveryMode(){return v(p+"delivery-mode","async");}public boolean failOpen(){return bool(p+"fail-open",true);}public String serviceUrl(){return v(p+"service-url","https://pubsub.googleapis.com");}public Optional<String> projectId(){return opt(p+"project-id");}public Optional<String> topic(){return opt(p+"topic");}public Duration timeout(){return duration(p+"timeout",Duration.ofSeconds(5));}public Optional<String> tlsProfile(){return opt(p+"tls-profile");}public Optional<ProxyProperties.HistoryExecutorConfig> executor(){return has(p+"executor.")?Optional.of(new Executor(p+"executor.")):Optional.empty();}}
  private final class Executor implements ProxyProperties.HistoryExecutorConfig {private final String p;Executor(String p){this.p=p;}public int coreThreads(){return integer(p+"core-threads",2);}public int maxThreads(){return integer(p+"max-threads",8);}public int queueCapacity(){return integer(p+"queue-capacity",1000);}}
  private final class BackendHistory implements ProxyProperties.BackendHistoryConfig {private final String p;BackendHistory(String p){this.p=p;}public Optional<Boolean> enabled(){return opt(p+"enabled").map(Boolean::parseBoolean);}public Optional<String> provider(){return opt(p+"provider");}public Optional<String> deliveryMode(){return opt(p+"delivery-mode");}public Optional<Boolean> failOpen(){return opt(p+"fail-open").map(Boolean::parseBoolean);}public Optional<String> serviceUrl(){return opt(p+"service-url");}public Optional<String> projectId(){return opt(p+"project-id");}public Optional<String> topic(){return opt(p+"topic");}public Optional<String> tokenHeader(){return opt(p+"token-header");}public Map<String,String> additionalProperties(){return map(p+"additional-properties.");}public Map<String,String> generatedHeaders(){return map(p+"generated-headers.");}}
  private final class Tls implements ProxyProperties.TlsConfig {public Map<String,ProxyProperties.TlsProfile> profiles(){Map<String,ProxyProperties.TlsProfile> r=new LinkedHashMap<>();String root="gateway.tls.profiles.";values.keySet().forEach(k->{if(k.startsWith(root)){String n=k.substring(root.length()).split("\\.")[0];r.put(n,new TProfile(root+n+"."));}});return Map.copyOf(r);}}
  private final class TProfile implements ProxyProperties.TlsProfile {private final String p;TProfile(String p){this.p=p;}public Optional<ProxyProperties.StoreConfig> truststore(){return has(p+"truststore.")?Optional.of(new Store(p+"truststore.")):Optional.empty();}public Optional<ProxyProperties.StoreConfig> keystore(){return has(p+"keystore.")?Optional.of(new Store(p+"keystore.")):Optional.empty();}}
  private final class Store implements ProxyProperties.StoreConfig {private final String p;Store(String p){this.p=p;required(p+"path");}public String path(){return v(p+"path",null);}public Optional<String> password(){return opt(p+"password");}public String type(){return v(p+"type","PKCS12");}public Optional<String> keyPassword(){return opt(p+"key-password");}}
  private final class Soap implements ProxyProperties.SoapConfig {private final String p;Soap(String p){this.p=p;}public String version(){return v(p+"version","1.1");}public Optional<String> soapAction(){return opt(p+"soap-action");}}
  private final class Proxy implements ProxyProperties.ProxyConfig {private final String p;Proxy(String p){this.p=p;required(p+"host");}public String host(){return v(p+"host",null);}public int port(){return integer(p+"port",0);}public List<String> nonProxyHosts(){return list(p+"non-proxy-hosts");}}
}
