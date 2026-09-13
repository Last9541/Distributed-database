package cmd.lsmkv;

public class Parser {


    public static String[] parse(char[] line)
    {
        int len=line.length;
        StringBuilder builder=new StringBuilder();
        String[] words=new String[len];
        int count=0;
        int isString=0;
        boolean isSpace=true;
        for(int i=0;i<len;i++)
        {
            if(line[i]!=' ')
                isSpace=false;
            switch (line[i])
            {
                case '\'':
                    if(isString==2)
                    {
                        words[count++]=builder.toString();
                        builder.setLength(0);
                        isString=0;
                        break;
                    }
                    if(isString==0) {
                        isString = 2;
                        break;
                    }
                    //ovde ce biti isString 1
                    builder.append(line[i]);
                    break;
                case '\"':
                    if(isString==1)
                    {
                        words[count++]=builder.toString();
                        builder.setLength(0);
                        isString=0;
                        break;
                    }
                    if(isString==0)
                    {
                        isString=1;
                        break;
                    }
                    //ovde ce biti isString 2
                    builder.append(line[i]);
                    break;
                case ' ':
                    if(isSpace) break;
                    if(isString!=0)
                    {
                        builder.append(line[i]);
                    }
                    else
                    {
                        if(!builder.isEmpty()) {
                            words[count++] = builder.toString();
                            builder.setLength(0);
                        }
                        isSpace=true;
                    }

                    break;
                default:
                    builder.append(line[i]);

            }
        }
        if(isString!=0)
            throw new IllegalArgumentException("Los unos stringa");
        if(!builder.isEmpty()) {
            words[count++] = builder.toString();
            builder.setLength(0);
        }
        String[] returnArr=new String[count];
        System.arraycopy(words, 0, returnArr, 0, count);
        return returnArr;
    }

}
