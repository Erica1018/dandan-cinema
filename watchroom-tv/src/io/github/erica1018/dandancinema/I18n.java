package io.github.erica1018.dandancinema;
final class I18n {
    static boolean chinese=java.util.Locale.getDefault().getLanguage().equals("zh");
    static String t(String zh,String en){return chinese?zh:en;}
    static String category(String value){
        if(chinese)return value;
        switch(value){case "电影":return "Film";case "剧集":return "Series";case "纪录片":return "Documentary";case "视频":return "Video";case "课程":return "Course";default:return value;}
    }
}
